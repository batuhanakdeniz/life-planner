#!/usr/bin/env python3
"""
Bülten yardımcısı: zamanlanmış Claude görevi bu betiği kullanır.

  bulletin.py pending                       → hazırlanması gereken günler (JSON)
  bulletin.py collect --date 2026-10-14     → videoları ve altyazıları toplar, manifest.json yazar
  bulletin.py publish --date 2026-10-14 --file sonuc.json
                                            → Claude'un yazdığı özetleri backend'e gönderir

Çıktılar ~/.lifeplanner/bulletin/<tarih>/ altında tutulur (depoya girmez).
Gereken: yt-dlp, çalışan backend (varsayılan http://localhost:8080, LIFEPLANNER_API ile değişir).
"""
import argparse
import html
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
from datetime import datetime, timezone
from pathlib import Path

API = os.environ.get("LIFEPLANNER_API", "http://localhost:8080/api").rstrip("/")
WORK = Path(os.environ.get("LIFEPLANNER_BULLETIN_DIR", Path.home() / ".lifeplanner" / "bulletin"))
PLAYLIST_SCAN = 300         # kanal listesinde en fazla kaç video taransın (yaklaşık tarihle, hızlı)
MAX_VIDEOS_PER_CHANNEL = 15 # kanal başına günde en fazla kaç video özetlensin
APPROX_MARGIN_H = 50        # yaklaşık tarih "1 gün önce" gibi kaba olabilir; pencereyi bu kadar genişlet
SUB_DELAY = 3               # altyazı istekleri arası bekleme (sn) — YouTube 429 vermesin
SUB_RETRY_DELAY = 45        # 429 alınırsa tekrar denemeden önce bekleme (sn)
PARAGRAPH_SECONDS = 30      # transcript'te kaç saniyede bir zaman damgası
DESCRIPTION_LIMIT = 1500


# ---------------------------------------------------------------- API
def api(method, path, body=None):
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(API + path, data=data, method=method,
                                 headers={"Content-Type": "application/json"} if data else {})
    try:
        with urllib.request.urlopen(req, timeout=30) as r:
            raw = r.read()
            return json.loads(raw) if raw else None
    except urllib.error.HTTPError as e:
        detail = e.read().decode(errors="replace")
        sys.exit(f"API hatası {e.code} {path}: {detail}")
    except urllib.error.URLError as e:
        sys.exit(f"Backend'e ulaşılamadı ({API}): {e.reason}. Backend çalışıyor mu?")


# ---------------------------------------------------------------- yt-dlp
def ytdlp(*args, timeout=120):
    return subprocess.run(["yt-dlp", "--no-warnings", "--ignore-config", *args],
                          capture_output=True, text=True, timeout=timeout)


def list_recent(channel_url):
    """Kanalın en yeni videoları ve yaklaşık yayın zamanları (UTC epoch ya da None).
    'approximate_date', listedeki "3 saat önce" gibi ifadelerden tarih çıkarır; tek istekte, video başına ek istek yok."""
    r = ytdlp("--flat-playlist", "--playlist-end", str(PLAYLIST_SCAN),
              "--extractor-args", "youtubetab:approximate_date",
              "--print", "%(id)s %(timestamp)s", channel_url, timeout=180)
    if r.returncode != 0:
        raise RuntimeError(last_error(r.stderr) or "video listesi alınamadı")
    out = []
    for line in r.stdout.splitlines():
        parts = line.split()
        if len(parts) == 2:
            out.append((parts[0], int(parts[1]) if parts[1].isdigit() else None))
    return out


def candidates(entries, start, end):
    """Yaklaşık tarihi pencereye (geniş payla) düşen videolar. Tarihi bilinmeyenler de aday sayılır."""
    lo = start.timestamp() - APPROX_MARGIN_H * 3600
    hi = end.timestamp() + 2 * 3600
    return [(vid, ts) for vid, ts in entries if ts is None or lo <= ts <= hi]


def video_meta(video_id):
    r = ytdlp("-J", "--skip-download", f"https://www.youtube.com/watch?v={video_id}")
    if r.returncode != 0:
        raise RuntimeError(last_error(r.stderr) or "video bilgisi alınamadı")
    return json.loads(r.stdout)


def last_error(stderr):
    lines = [l for l in stderr.strip().splitlines() if "ERROR" in l]
    return (lines[-1] if lines else stderr.strip()[-300:]).replace("ERROR: ", "")


def published_at(meta):
    ts = meta.get("release_timestamp") or meta.get("timestamp")
    if ts:
        return datetime.fromtimestamp(ts, tz=timezone.utc)
    d = meta.get("upload_date")  # yalnızca gün biliniyorsa gün ortası varsay
    return datetime.strptime(d, "%Y%m%d").replace(hour=12, tzinfo=timezone.utc) if d else None


def pick_subtitle(meta):
    """Tek bir altyazı seç: önce elle yazılmış, sonra otomatik; videonun kendi dili > Türkçe > İngilizce."""
    lang = (meta.get("language") or "").split("-")[0]
    prefs = [l for l in [lang, "tr", "en"] if l]
    manual = meta.get("subtitles") or {}
    auto = meta.get("automatic_captions") or {}
    for p in prefs:
        for key in manual:
            if key == p or key.startswith(p + "-"):
                return key, False
    for p in prefs:
        for key in (f"{p}-orig", p):  # "-orig": konuşulan dilin orijinal otomatik altyazısı
            if key in auto:
                return key, True
    return None, None


def download_subtitle(video_id, sub_lang, automatic, out_dir):
    out = out_dir / video_id
    args = ["--skip-download", "--sub-format", "vtt", "--sub-langs", sub_lang,
            "--write-auto-subs" if automatic else "--write-subs", "-o", str(out),
            f"https://www.youtube.com/watch?v={video_id}"]
    for attempt in range(2):
        r = ytdlp(*args)
        files = list(out_dir.glob(f"{video_id}*.vtt"))
        if files:
            return files[0]
        if "429" in r.stderr and attempt == 0:
            print(f"    429 (çok fazla istek), {SUB_RETRY_DELAY} sn bekleniyor…", file=sys.stderr)
            time.sleep(SUB_RETRY_DELAY)
            continue
        raise RuntimeError(last_error(r.stderr) or "altyazı indirilemedi")
    raise RuntimeError("altyazı indirilemedi (429)")


# ---------------------------------------------------------------- VTT → metin
CUE_TIME = re.compile(r"(\d+):(\d{2}):(\d{2})\.\d+\s+-->")


def vtt_to_text(vtt):
    """Otomatik altyazılardaki tekrarlanan satırları atar; ~30 sn'de bir [dk:sn] damgası koyar."""
    paragraphs, current, current_start, last_line = [], [], None, None
    cue_start = 0
    for raw in vtt.splitlines():
        m = CUE_TIME.match(raw)
        if m:
            h, mnt, s = map(int, m.groups())
            cue_start = h * 3600 + mnt * 60 + s
            continue
        line = raw.strip()
        if not line or line.startswith(("WEBVTT", "Kind:", "Language:", "NOTE")) or "-->" in line:
            continue
        line = html.unescape(re.sub(r"<[^>]+>", "", line)).strip()
        if not line or line == last_line or line in ("[Music]", "[Müzik]", "[music]"):
            continue
        last_line = line
        if current_start is None:
            current_start = cue_start
        if cue_start - current_start >= PARAGRAPH_SECONDS and current:
            paragraphs.append((current_start, " ".join(current)))
            current, current_start = [], cue_start
        current.append(line)
    if current:
        paragraphs.append((current_start or 0, " ".join(current)))
    return "\n".join(f"[{clock(t)}] {text}" for t, text in paragraphs)


def clock(seconds):
    h, rem = divmod(int(seconds), 3600)
    m, s = divmod(rem, 60)
    return f"{h}:{m:02d}:{s:02d}" if h else f"{m}:{s:02d}"


def parse_iso(s):
    return datetime.fromisoformat(s.replace("Z", "+00:00"))


# ---------------------------------------------------------------- komutlar
def cmd_pending(_):
    print(json.dumps(api("GET", "/bulletins/pending")))


def cmd_collect(args):
    plan = api("GET", f"/bulletins/plan?date={args.date}")
    out_dir = WORK / args.date
    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = {"date": args.date, "generatedAt": datetime.now(timezone.utc).isoformat(), "channels": []}

    for ch in plan["channels"]:
        start, end = parse_iso(ch["windowStart"]), parse_iso(ch["windowEnd"])
        entry = {k: ch[k] for k in ("channelId", "name", "handle", "windowStart", "windowEnd")}
        entry.update(error=None, videos=[])
        print(f"• {ch['name']} ({ch['handle']}) {start:%d.%m %H:%M} → {end:%d.%m %H:%M} UTC", file=sys.stderr)
        try:
            ids = candidates(list_recent(ch["url"]), start, end)
        except Exception as e:  # noqa: BLE001 — kanal hatası diğer kanalları durdurmasın
            entry["error"] = f"Video listesi alınamadı: {e}"
            manifest["channels"].append(entry)
            continue

        in_window = 0
        older_streak = 0
        for idx, (vid, _approx) in enumerate(ids):
            if in_window >= MAX_VIDEOS_PER_CHANNEL:
                # Sınır doldu: kalanları ek istek yapmadan yaklaşık tarihle say
                s_ts, e_ts = start.timestamp(), end.timestamp()
                entry["skippedVideos"] = sum(1 for _, ts in ids[idx:] if ts is not None and s_ts <= ts < e_ts)
                break
            try:
                meta = video_meta(vid)
            except Exception as e:  # noqa: BLE001
                print(f"    {vid}: bilgi alınamadı ({e})", file=sys.stderr)
                continue
            if meta.get("live_status") in ("is_upcoming", "is_live"):
                continue
            pub = published_at(meta)
            if pub is None or pub >= end:
                continue  # aralıktan sonra yayınlanmış
            if pub < start:
                older_streak += 1
                if older_streak >= 3:  # liste yeniden eskiye; 3 eski üst üste görünce dur
                    break
                continue
            older_streak = 0
            in_window += 1
            video = {
                "videoId": vid,
                "title": meta.get("title") or vid,
                "publishedAt": pub.isoformat().replace("+00:00", "Z"),
                "durationSeconds": meta.get("duration"),
                "description": (meta.get("description") or "")[:DESCRIPTION_LIMIT],
                "chapters": [{"seconds": int(c["start_time"]), "label": c["title"]} for c in (meta.get("chapters") or [])],
                "hasTranscript": False,
                "transcriptFile": None,
            }
            sub_lang, automatic = pick_subtitle(meta)
            if sub_lang:
                try:
                    time.sleep(SUB_DELAY)
                    vtt = download_subtitle(vid, sub_lang, automatic, out_dir)
                    text = vtt_to_text(vtt.read_text(encoding="utf-8", errors="replace"))
                    if text.strip():
                        tfile = out_dir / f"{vid}.txt"
                        tfile.write_text(text, encoding="utf-8")
                        video.update(hasTranscript=True, transcriptFile=str(tfile), transcriptLanguage=sub_lang)
                    vtt.unlink(missing_ok=True)
                except Exception as e:  # noqa: BLE001
                    video["transcriptError"] = str(e)
            print(f"    ✓ {video['title'][:70]} ({'altyazı' if video['hasTranscript'] else 'altyazı yok'})", file=sys.stderr)
            entry["videos"].append(video)
        manifest["channels"].append(entry)

    path = out_dir / "manifest.json"
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=2), encoding="utf-8")
    total = sum(len(c["videos"]) for c in manifest["channels"])
    print(json.dumps({"manifest": str(path), "channels": len(manifest["channels"]), "videos": total}, ensure_ascii=False))


def cmd_publish(args):
    result = json.loads(Path(args.file).read_text(encoding="utf-8"))
    if "channels" not in result:
        sys.exit("Sonuç dosyasında 'channels' alanı yok")
    for c in result["channels"]:
        for k in ("channelId", "windowStart", "windowEnd"):
            if k not in c:
                sys.exit(f"Kanal kaydında '{k}' eksik: {c.get('channelId')}")
    saved = api("PUT", f"/bulletins/{args.date}", result)
    n = sum(len(c["videos"]) for c in saved["channels"])
    print(json.dumps({"saved": args.date, "channels": len(saved["channels"]), "videos": n}, ensure_ascii=False))


def main():
    p = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = p.add_subparsers(dest="cmd", required=True)
    sub.add_parser("pending").set_defaults(fn=cmd_pending)
    c = sub.add_parser("collect")
    c.add_argument("--date", required=True)
    c.set_defaults(fn=cmd_collect)
    pub = sub.add_parser("publish")
    pub.add_argument("--date", required=True)
    pub.add_argument("--file", required=True)
    pub.set_defaults(fn=cmd_publish)
    args = p.parse_args()
    args.fn(args)


if __name__ == "__main__":
    main()
