# Günlük bülten görevi

Bu dosya, her gün 12:05'te çalışan zamanlanmış Claude görevinin talimatıdır.
Özet kurallarını değiştirmek için bu dosyayı düzenlemen yeterli; kod değişikliği gerekmez.

## Adımlar

Proje klasörü: `/Users/batuhanakdeniz/Desktop/repos/demo`. Tüm komutları orada çalıştır.

1. **Backend'in çalıştığından emin ol.**
   `curl -s -m 5 http://localhost:8080/api/bulletins/pending` yanıt vermiyorsa backend'i arka planda başlat:
   `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home ./mvnw -q spring-boot:run > /tmp/lifeplanner-backend.log 2>&1 &`
   Ardından en fazla 90 saniye boyunca 3 saniyede bir tekrar dene. Açılmazsa logun son satırlarını raporla ve dur.

2. **Bekleyen günleri al:** `python3 tools/bulletin/bulletin.py pending` → JSON tarih listesi (eskiden yeniye).
   Liste boşsa "Bekleyen bülten yok" diye raporla ve bitir. Kaçırılmış günler varsa hepsini sırayla işle.

3. **Her gün için:**
   1. `python3 tools/bulletin/bulletin.py collect --date <TARİH>` çalıştır (birkaç dakika sürebilir).
      Son satırdaki JSON'da `manifest` yolu var.
   2. Manifest'i oku. **Hiç kanal yoksa** (`channels` boş) o gün için bülten yayınlama, "Takip edilen kanal yok" diye raporla ve sonraki güne geç.
   3. Her videonun `transcriptFile` dosyasını oku ve aşağıdaki kurallara göre özetle.
   4. Sonucu manifest ile aynı klasöre `result.json` olarak yaz (şema aşağıda).
   5. `python3 tools/bulletin/bulletin.py publish --date <TARİH> --file <klasör>/result.json` ile gönder.

4. **Rapor:** her gün için kanal ve video sayısını, varsa hataları kısa bir Türkçe özetle bildir.

Kod dosyalarını değiştirme, commit atma. Sadece yukarıdaki komutları kullan.

## Özet kuralları

- **Dil:** her zaman Türkçe. Video İngilizce ya da başka dilde olsa da özet Türkçe yazılır. Özel isimler, ürün adları ve hisse kodları olduğu gibi kalır.
- **Doğruluk:** yalnızca transcript'te (ya da altyazı yoksa başlık/açıklamada) geçenleri yaz. Tahmin, yorum ya da dış bilgi ekleme. Rakamları transcript'teki gibi aktar.
- **Alıntı:** transcript'ten uzun parça kopyalama; kendi cümlelerinle özetle.
- **`overview` (Günün özeti):** tüm kanalları kapsayan 2-4 cümle. Günün öne çıkan konularını birleştir.
- **Kanal `shortSummary`:** o kanalın o günkü videolarının 1-3 cümlelik ortak özeti. Video yoksa `null`.
- **Video `summary`:** 2-4 cümle; konuşmacı/kanal ne anlatıyor, ana iddia ya da sonuç ne.
- **`keyPoints`:** 3-8 madde; her biri tek cümle, somut (rakam, karar, öneri).
- **`chapters`:** 3-10 bölüm. Manifest'te videonun kendi `chapters` listesi varsa onları kullan (başlıkları Türkçeye çevir). Yoksa transcript'teki `[dk:sn]` damgalarından konu değişimlerini çıkar; `seconds` alanı damganın saniye karşılığıdır.
- **`mentions`:** `people` (kişiler), `companies` (şirket/kurum), `numbers` (önemli rakamlar, kısa ifadeyle: "%3 faiz indirimi"). Yoksa boş liste.
- **Altyazı yoksa** (`hasTranscript: false`): başlık ve açıklamadan 1-2 cümlelik not yaz; `keyPoints` ve `chapters` boş kalabilir.
- **Kanal hatası** (`error` dolu): `error` alanını aynen aktar, `videos` boş.

## result.json şeması

```json
{
  "overview": "Günün özeti…",
  "channels": [
    {
      "channelId": 1,
      "windowStart": "manifest'teki değer",
      "windowEnd": "manifest'teki değer",
      "skippedVideos": 0,
      "error": null,
      "shortSummary": "Kanalın kısa özeti…",
      "videos": [
        {
          "videoId": "manifest'teki değer",
          "title": "manifest'teki değer",
          "publishedAt": "manifest'teki değer",
          "durationSeconds": 1073,
          "hasTranscript": true,
          "summary": "…",
          "keyPoints": ["…"],
          "chapters": [{ "seconds": 252, "label": "…" }],
          "mentions": { "people": [], "companies": [], "numbers": [] }
        }
      ]
    }
  ]
}
```

`channelId`, `windowStart`, `windowEnd`, `skippedVideos`, `error` ve videoların `videoId`, `title`, `publishedAt`, `durationSeconds`, `hasTranscript` alanları manifest'ten aynen kopyalanır. Manifest'teki her kanal sonuçta yer almalıdır (videosu olmasa bile).
