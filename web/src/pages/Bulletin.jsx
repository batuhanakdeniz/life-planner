import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { formatDate } from '../util.js'

const time = (iso) => new Date(iso).toLocaleString('tr-TR', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit', timeZone: 'Europe/Istanbul' })
const clock = (s) => {
  const h = Math.floor(s / 3600), m = Math.floor((s % 3600) / 60), sec = s % 60
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(sec).padStart(2, '0')}` : `${m}:${String(sec).padStart(2, '0')}`
}

export default function Bulletin() {
  const [dates, setDates] = useState(null)
  const [date, setDate] = useState(null)
  const [bulletin, setBulletin] = useState(undefined)
  const [channels, setChannels] = useState([])
  const [showChannels, setShowChannels] = useState(false)
  const [error, setError] = useState(null)

  useEffect(() => {
    Promise.all([api.bulletinDates(), api.bulletinChannels()])
      .then(([d, c]) => { setDates(d); setChannels(c); setDate(d[0] ?? null) })
      .catch((e) => setError(e.message))
  }, [])

  useEffect(() => {
    if (dates === null) return
    if (!date) { setBulletin(null); return }
    setBulletin(undefined)
    api.bulletin(date).then(setBulletin).catch((e) => setError(e.message))
  }, [date, dates])

  const idx = dates?.indexOf(date) ?? -1
  const older = dates && idx >= 0 && idx < dates.length - 1 ? dates[idx + 1] : null
  const newer = dates && idx > 0 ? dates[idx - 1] : null

  return (
    <>
      <div className="row">
        <h1>📰 Bülten</h1>
        <div className="spacer" />
        {dates?.length > 0 && (
          <>
            <button className="btn" disabled={!older} onClick={() => setDate(older)} title="Önceki bülten">‹</button>
            <select value={date ?? ''} onChange={(e) => setDate(e.target.value)}>
              {dates.map((d) => <option key={d} value={d}>{formatDate(d, { day: 'numeric', month: 'long', weekday: 'short' })}</option>)}
            </select>
            <button className="btn" disabled={!newer} onClick={() => setDate(newer)} title="Sonraki bülten">›</button>
          </>
        )}
        <button className={`btn ${showChannels ? 'on' : ''}`} onClick={() => setShowChannels(!showChannels)}>Kanallar ({channels.filter((c) => c.active).length})</button>
      </div>
      <p className="subtitle">Takip ettiğin YouTube kanallarının günlük özeti. Her gün 12:05'te hazırlanır.</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      {showChannels && <ChannelList channels={channels} />}

      {bulletin === undefined && <p className="muted">Yükleniyor…</p>}
      {bulletin === null && (
        <section className="card empty-state">
          <h2>{channels.length === 0 ? 'Takip edilecek kanal yok' : 'Henüz bülten yok'}</h2>
          <p className="muted">
            {channels.length === 0
              ? "Obsidian'da Bülten/Kanallar.md tablosuna kanal ekle (ör. @kanaladi). En geç 5 dakika içinde buraya gelir."
              : 'İlk bülten bir sonraki 12:05 çalışmasında hazırlanacak.'}
          </p>
        </section>
      )}
      {bulletin && <BulletinView b={bulletin} />}
    </>
  )
}

function ChannelList({ channels }) {
  return (
    <section className="card" style={{ marginBottom: 16 }}>
      <div className="row" style={{ marginBottom: 6 }}>
        <h2 style={{ margin: 0 }}>Takip edilen kanallar</h2>
        <div className="spacer" />
        <span className="muted">Düzenlemek için: Obsidian → Bülten/Kanallar.md ya da bulletin_channels tablosu</span>
      </div>
      <ul className="list">
        {channels.length === 0 && <li className="empty">Henüz kanal yok</li>}
        {channels.map((c) => (
          <li key={c.id} className={`item ${c.active ? '' : 'done'}`}>
            <span className="title">{c.name} <a className="muted" href={c.url} target="_blank" rel="noreferrer">{c.handle}</a></span>
            <span className="tag">{c.cutoffTime.slice(0, 5)} kesim · {c.lookbackHours} saat geriye</span>
            {!c.active && <span className="tag">pasif</span>}
          </li>
        ))}
      </ul>
    </section>
  )
}

function BulletinView({ b }) {
  const [open, setOpen] = useState(() => new Set())
  const toggle = (key) => setOpen((s) => { const n = new Set(s); n.has(key) ? n.delete(key) : n.add(key); return n })
  const totalVideos = b.channels.reduce((n, c) => n + c.videos.length, 0)

  return (
    <>
      <section className="card digest">
        <div className="row" style={{ marginBottom: 8 }}>
          <h2 style={{ margin: 0 }}>Günün özeti</h2>
          <div className="spacer" />
          <span className="muted">{formatDate(b.date)} · {b.channels.length} kanal · {totalVideos} video</span>
        </div>
        {b.overview && <p className="overview">{b.overview}</p>}
        <ul className="digest-list">
          {b.channels.map((c, i) => (
            <li key={i}>
              <button className="link-btn digest-name" onClick={() => { toggle(i); document.getElementById(`ch-${i}`)?.scrollIntoView({ behavior: 'smooth', block: 'start' }) }}>{c.channelName}</button>
              {' · '}
              {c.error ? <span className="neg">{c.error}</span>
                : c.videos.length === 0 ? <span className="muted">Bu aralıkta video yok.</span>
                  : <span>{c.shortSummary}</span>}
            </li>
          ))}
        </ul>
      </section>

      {b.channels.map((c, i) => (
        <section key={i} id={`ch-${i}`} className="card channel-card">
          <button className="channel-head" onClick={() => toggle(i)} aria-expanded={open.has(i)}>
            <span className="channel-name">{c.channelName}</span>
            <span className="muted">{c.channelHandle}</span>
            <div className="spacer" />
            <span className="muted">{time(c.windowStart)} → {time(c.windowEnd)}</span>
            <span className="tag">{c.videos.length} video{c.skippedVideos > 0 ? ` · ≈${c.skippedVideos} daha` : ''}</span>
            <span className="chev">{open.has(i) ? '▾' : '▸'}</span>
          </button>
          {open.has(i) && (
            <div className="channel-body">
              {c.shortSummary && <p className="muted">{c.shortSummary}</p>}
              {c.skippedVideos > 0 && <p className="warn small">≈{c.skippedVideos} video daha yayınlandı; kanal başına günlük sınır nedeniyle özetlenmedi.</p>}
              {c.videos.length === 0 && <p className="empty">Bu aralıkta video yok.</p>}
              {c.videos.map((v) => <Video key={v.videoId} v={v} />)}
            </div>
          )}
        </section>
      ))}
    </>
  )
}

function Video({ v }) {
  const [open, setOpen] = useState(false)
  const m = v.mentions ?? {}
  return (
    <article className="video">
      <button className="video-head" onClick={() => setOpen(!open)} aria-expanded={open}>
        <span className="chev">{open ? '▾' : '▸'}</span>
        <span className="video-title">{v.title}</span>
        {v.durationSeconds != null && <span className="tag">{Math.round(v.durationSeconds / 60)} dk</span>}
        {!v.hasTranscript && <span className="tag overdue" title="Altyazı bulunamadı; özet başlık ve açıklamadan çıkarıldı">altyazı yok</span>}
      </button>
      <div className="video-sub">
        {v.publishedAt && <span className="muted">{time(v.publishedAt)}</span>}
        {' · '}<a href={v.url} target="_blank" rel="noreferrer">YouTube'da aç ↗</a>
      </div>
      {v.summary && <p className="video-summary">{v.summary}</p>}
      {open && (
        <div className="video-detail">
          {v.keyPoints.length > 0 && (
            <>
              <h3>Önemli noktalar</h3>
              <ul>{v.keyPoints.map((p, i) => <li key={i}>{p}</li>)}</ul>
            </>
          )}
          {v.chapters.length > 0 && (
            <>
              <h3>Bölümler</h3>
              <ul className="chapters">
                {v.chapters.map((c, i) => (
                  <li key={i}><a href={`${v.url}&t=${c.seconds}s`} target="_blank" rel="noreferrer" className="ts">{clock(c.seconds)}</a> {c.label}</li>
                ))}
              </ul>
            </>
          )}
          <Mentions label="Kişiler" items={m.people} />
          <Mentions label="Şirketler / kurumlar" items={m.companies} />
          <Mentions label="Rakamlar" items={m.numbers} />
        </div>
      )}
      {!open && (v.keyPoints.length > 0 || v.chapters.length > 0) && (
        <button className="link-btn" onClick={() => setOpen(true)}>Detayları göster ({v.keyPoints.length} nokta, {v.chapters.length} bölüm)</button>
      )}
    </article>
  )
}

function Mentions({ label, items }) {
  if (!items?.length) return null
  return (
    <div className="mentions">
      <span className="muted">{label}:</span>
      {items.map((x) => <span key={x} className="tag">{x}</span>)}
    </div>
  )
}
