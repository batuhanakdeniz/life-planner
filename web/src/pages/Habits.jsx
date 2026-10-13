import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { addDays, DAYS, formatDate, startOfWeek, today } from '../util.js'

const COLORS = ['#a78bfa', '#22d3ee', '#4ade80', '#fbbf24', '#f472b6', '#fb923c', '#60a5fa', '#f87171']

export default function Habits() {
  const [monday, setMonday] = useState(startOfWeek(today()))
  const [habits, setHabits] = useState([])
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null) // null | 'new' | habit
  const [showArchived, setShowArchived] = useState(false)
  const [error, setError] = useState(null)
  const sunday = addDays(monday, 6)
  const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i))
  const now = today()

  // Pazar gününü referans verince last7Days tam olarak Pzt–Paz haftasını kapsar
  const load = useCallback(() => {
    api.habits(sunday, showArchived).then(setHabits).catch((e) => setError(e.message))
  }, [sunday, showArchived])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))
  const selectedHabit = habits.find((h) => h.id === selected)

  return (
    <>
      <div className="row">
        <h1>🔁 Alışkanlıklar</h1>
        <div className="spacer" />
        <button className="btn primary" onClick={() => setEditing('new')}>+ Yeni alışkanlık</button>
      </div>
      <p className="subtitle">Bir güne tıklayarak işaretle; detay ve geçmiş için alışkanlığın adına tıkla.</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      {editing && (
        <HabitForm key={editing === 'new' ? 'new' : editing.id} habit={editing === 'new' ? null : editing} onCancel={() => setEditing(null)}
          onSave={(data) => run(() => (editing === 'new' ? api.createHabit(data) : api.updateHabit(editing.id, data))).then(() => setEditing(null))} />
      )}

      <section className="card">
        <div className="row" style={{ marginBottom: 12 }}>
          <button className="btn" onClick={() => setMonday(addDays(monday, -7))}>‹</button>
          <strong>{formatDate(monday, { day: 'numeric', month: 'long' })} – {formatDate(sunday, { day: 'numeric', month: 'long' })}</strong>
          <button className="btn" onClick={() => setMonday(addDays(monday, 7))} disabled={sunday >= now}>›</button>
          <div className="spacer" />
          <label className="muted row"><input type="checkbox" checked={showArchived} onChange={(e) => setShowArchived(e.target.checked)} /> Arşivi göster</label>
        </div>
        <div className="table-wrap">
          <table className="habit-table">
            <thead>
              <tr>
                <th />
                {days.map((d) => (
                  <th key={d} className={d === now ? 'today' : ''}>
                    {formatDate(d, { weekday: 'short' })}<br /><span className="muted">{formatDate(d, { day: 'numeric' })}</span>
                  </th>
                ))}
                <th>Seri</th>
              </tr>
            </thead>
            <tbody>
              {habits.length === 0 && <tr><td colSpan={9} className="empty">Henüz alışkanlık yok. "+ Yeni alışkanlık" ile başla.</td></tr>}
              {habits.map((h) => (
                <tr key={h.id} style={{ '--hc': h.color || undefined }} className={`${h.archived ? 'archived' : ''} ${selected === h.id ? 'selected' : ''}`}>
                  <td className="habit-name" onClick={() => setSelected(selected === h.id ? null : h.id)}>
                    <span className="habit-icon">{h.icon || '•'}</span> {h.name}
                  </td>
                  {h.last7Days.map((d) => {
                    const future = d.date > now
                    return (
                      <td key={d.date} className="cell">
                        {d.scheduled ? (
                          <button className={`cell-btn ${d.done ? 'done' : ''}`} disabled={future || h.archived}
                            title={future ? 'Gelecek gün' : d.done ? 'Geri al' : 'Yapıldı olarak işaretle'}
                            onClick={() => run(() => api.setHabitDone(h.id, d.date, !d.done))}>
                            {d.done ? '✓' : ''}
                          </button>
                        ) : <span className="off-day" title="Bu gün planlı değil">·</span>}
                      </td>
                    )
                  })}
                  <td className="streak">{h.currentStreak > 0 ? `🔥 ${h.currentStreak}` : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>

      {selectedHabit && (
        <HabitDetail habit={selectedHabit} onEdit={() => setEditing(selectedHabit)}
          onArchive={() => run(() => api.updateHabit(selectedHabit.id, { ...selectedHabit, archived: !selectedHabit.archived }))}
          onDelete={() => confirm(`"${selectedHabit.name}" ve tüm geçmişi silinsin mi?`) && run(() => api.deleteHabit(selectedHabit.id)).then(() => setSelected(null))} />
      )}
    </>
  )
}

function HabitForm({ habit, onSave, onCancel }) {
  const [name, setName] = useState(habit?.name ?? '')
  const [icon, setIcon] = useState(habit?.icon ?? '')
  const [color, setColor] = useState(habit?.color ?? COLORS[0])
  const [days, setDays] = useState(habit?.days ?? [])
  const [description, setDescription] = useState(habit?.description ?? '')

  const toggleDay = (d) => setDays(days.includes(d) ? days.filter((x) => x !== d) : [...days, d])

  const submit = (e) => {
    e.preventDefault()
    if (!name.trim()) return
    onSave({ name: name.trim(), icon: icon || null, color, days, description: description || null, archived: habit?.archived ?? false })
  }

  return (
    <form className="card form" onSubmit={submit} style={{ marginBottom: 20 }}>
      <h2>{habit ? 'Alışkanlığı düzenle' : 'Yeni alışkanlık'}</h2>
      <div className="row">
        <input style={{ width: 64, textAlign: 'center' }} placeholder="📚" value={icon} onChange={(e) => setIcon(e.target.value)} title="Emoji" />
        <input style={{ flex: 1 }} placeholder="Ör. 20 sayfa kitap oku" value={name} onChange={(e) => setName(e.target.value)} autoFocus />
      </div>
      <input placeholder="Açıklama (isteğe bağlı)" value={description} onChange={(e) => setDescription(e.target.value)} />
      <div className="row">
        <span className="muted">Günler:</span>
        <div className="day-picker">
          {DAYS.map(([k, label]) => (
            <button type="button" key={k} className={days.includes(k) ? 'on' : ''} onClick={() => toggleDay(k)}>{label}</button>
          ))}
        </div>
        <span className="muted">{days.length === 0 ? '(seçilmezse her gün)' : ''}</span>
      </div>
      <div className="row">
        <span className="muted">Renk:</span>
        {COLORS.map((c) => (
          <button type="button" key={c} className={`swatch ${color === c ? 'on' : ''}`} style={{ background: c }} onClick={() => setColor(c)} />
        ))}
      </div>
      <div className="row">
        <button className="btn primary">Kaydet</button>
        <button type="button" className="btn" onClick={onCancel}>Vazgeç</button>
      </div>
    </form>
  )
}

const HEATMAP_WEEKS = 26

function HabitDetail({ habit, onEdit, onArchive, onDelete }) {
  const [done, setDone] = useState(new Set())
  const end = today()
  const start = startOfWeek(addDays(end, -7 * (HEATMAP_WEEKS - 1)))

  useEffect(() => {
    api.habitHistory(habit.id, start, end).then((dates) => setDone(new Set(dates)))
  }, [habit, start, end])

  const scheduledOn = (iso) => {
    if (habit.days.length === 0) return true
    const [y, m, d] = iso.split('-').map(Number)
    const dow = new Date(y, m - 1, d).getDay()
    return habit.days.includes(DAYS[(dow + 6) % 7][0])
  }

  const weeks = Array.from({ length: HEATMAP_WEEKS }, (_, w) =>
    Array.from({ length: 7 }, (_, d) => addDays(start, w * 7 + d)))
  // Başarı oranı yalnızca alışkanlığın başladığı günden itibaren hesaplanır
  const scheduledPast = weeks.flat().filter((d) => d >= habit.startDate && d <= end && scheduledOn(d)).length
  const rate = scheduledPast ? Math.round((done.size / scheduledPast) * 100) : 0

  return (
    <section className="card" style={{ marginTop: 20, '--hc': habit.color || undefined }}>
      <div className="row">
        <h2 style={{ margin: 0 }}>{habit.icon} {habit.name}</h2>
        <div className="spacer" />
        <button className="btn" onClick={onEdit}>Düzenle</button>
        <button className="btn" onClick={onArchive}>{habit.archived ? 'Arşivden çıkar' : 'Arşivle'}</button>
        <button className="btn danger" onClick={onDelete}>Sil</button>
      </div>
      {habit.description && <p className="muted">{habit.description}</p>}
      <div className="stats" style={{ margin: '16px 0' }}>
        <div className="card stat"><div className="value">🔥 {habit.currentStreak}</div><div className="label">Mevcut seri</div></div>
        <div className="card stat"><div className="value">⭐ {habit.longestStreak}</div><div className="label">En uzun seri</div></div>
        <div className="card stat"><div className="value">%{rate}</div><div className="label">Başarı oranı</div></div>
      </div>
      <div className="heatmap-wrap">
        <div className="heatmap-days">{DAYS.map(([k, l], i) => <span key={k}>{i % 2 === 0 ? l : ''}</span>)}</div>
        <div className="heatmap">
          {weeks.map((week) => (
            <div key={week[0]} className="heatmap-week">
              {week.map((d) => (
                <span key={d} title={`${formatDate(d, { day: 'numeric', month: 'long' })}${done.has(d) ? ' ✓' : ''}`}
                  className={`hm ${done.has(d) ? 'done' : ''} ${!scheduledOn(d) || d < habit.startDate ? 'off' : ''} ${d > end ? 'future' : ''}`} />
              ))}
            </div>
          ))}
        </div>
      </div>
    </section>
  )
}
