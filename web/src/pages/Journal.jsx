import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { addDays, DAYS, formatDate, MOODS, startOfWeek, today } from '../util.js'

const MOOD_LABELS = ['Çok kötü', 'Kötü', 'Normal', 'İyi', 'Harika']

const monthStart = (iso) => `${iso.slice(0, 7)}-01`
const monthEnd = (iso) => {
  const [y, m] = iso.split('-').map(Number)
  return `${iso.slice(0, 7)}-${String(new Date(y, m, 0).getDate()).padStart(2, '0')}`
}
const shiftMonth = (iso, n) => {
  const [y, m] = iso.split('-').map(Number)
  const d = new Date(y, m - 1 + n, 1)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
}

export default function Journal() {
  const [month, setMonth] = useState(monthStart(today()))
  const [selected, setSelected] = useState(today())
  const [entries, setEntries] = useState({})
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    api.journal(month, monthEnd(month))
      .then((list) => setEntries(Object.fromEntries(list.map((e) => [e.date, e]))))
      .catch((e) => setError(e.message))
  }, [month])
  useEffect(load, [load])

  // Takvim: ayın ilk haftasının pazartesisinden başlayan 6 hafta
  const gridStart = startOfWeek(month)
  const cells = Array.from({ length: 42 }, (_, i) => addDays(gridStart, i))
  const now = today()
  const moods = Object.values(entries).filter((e) => e.mood)
  const avgMood = moods.length ? moods.reduce((s, e) => s + e.mood, 0) / moods.length : null

  return (
    <>
      <h1>📓 Günlük</h1>
      <p className="subtitle">Her gün için kısa bir not ve ruh hali. Renkler o günün ruh halini gösterir.</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <div className="journal-layout">
        <section className="card">
          <div className="row" style={{ marginBottom: 12 }}>
            <button className="btn" onClick={() => setMonth(shiftMonth(month, -1))}>‹</button>
            <strong className="cap" style={{ flex: 1, textAlign: 'center' }}>{formatDate(month, { month: 'long', year: 'numeric' })}</strong>
            <button className="btn" onClick={() => setMonth(shiftMonth(month, 1))}>›</button>
          </div>
          <div className="cal">
            {DAYS.map(([k, l]) => <span key={k} className="cal-head">{l}</span>)}
            {cells.map((d) => {
              const e = entries[d]
              const outside = d.slice(0, 7) !== month.slice(0, 7)
              return (
                <button key={d} disabled={d > now}
                  className={`cal-day ${outside ? 'outside' : ''} ${d === selected ? 'sel' : ''} ${d === now ? 'now' : ''} ${e?.mood ? `mood-${e.mood}` : ''}`}
                  onClick={() => { setSelected(d); if (outside) setMonth(monthStart(d)) }}>
                  <span>{Number(d.slice(8))}</span>
                  {e && <span className="cal-dot">{e.mood ? MOODS[e.mood - 1] : '•'}</span>}
                </button>
              )
            })}
          </div>
          <div className="row muted" style={{ marginTop: 14, justifyContent: 'space-between' }}>
            <span>{Object.keys(entries).length} kayıt</span>
            {avgMood && <span>Ortalama ruh hali: {MOODS[Math.round(avgMood) - 1]} {avgMood.toFixed(1)}</span>}
          </div>
        </section>

        <Editor key={selected} date={selected} entry={entries[selected]} onSaved={load} onError={setError} />
      </div>
    </>
  )
}

function Editor({ date, entry, onSaved, onError }) {
  const [content, setContent] = useState(entry?.content ?? '')
  const [mood, setMood] = useState(entry?.mood ?? null)
  const [status, setStatus] = useState(null)

  // Kayıt listesi sonradan yüklenirse editörü doldur
  useEffect(() => {
    setContent(entry?.content ?? '')
    setMood(entry?.mood ?? null)
  }, [entry])

  const dirty = content !== (entry?.content ?? '') || mood !== (entry?.mood ?? null)

  const save = () => {
    setStatus('Kaydediliyor…')
    api.saveJournal(date, { content, mood })
      .then(() => { setStatus('Kaydedildi ✓'); onSaved() })
      .catch((e) => { setStatus(null); onError(e.message) })
  }

  return (
    <section className="card editor">
      <h2 className="cap">{formatDate(date, { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' })}</h2>
      <div className="moods">
        {MOODS.map((m, i) => (
          <button key={m} title={MOOD_LABELS[i]} className={mood === i + 1 ? 'on' : ''} onClick={() => setMood(mood === i + 1 ? null : i + 1)}>{m}</button>
        ))}
      </div>
      <textarea placeholder="Bugün neler oldu? Neye minnettarsın? Yarın için ne düşünüyorsun?" value={content}
        onChange={(e) => { setContent(e.target.value); setStatus(null) }}
        onKeyDown={(e) => (e.metaKey || e.ctrlKey) && e.key === 's' && (e.preventDefault(), save())} />
      <div className="row">
        <button className="btn primary" onClick={save} disabled={!dirty}>Kaydet</button>
        <span className="muted">{status ?? (dirty ? 'Kaydedilmemiş değişiklikler · ⌘S' : '')}</span>
      </div>
    </section>
  )
}
