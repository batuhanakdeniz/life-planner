import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { addDays, formatDate, MOODS, PRIORITY, today } from '../util.js'
import { PaymentRow } from './finance/PaymentsView.jsx'

export default function Today() {
  const [date, setDate] = useState(today())
  const [data, setData] = useState(null)
  const [error, setError] = useState(null)
  const [newTask, setNewTask] = useState('')

  const load = useCallback(() => {
    api.dashboard(date).then(setData).catch((e) => setError(e.message))
  }, [date])

  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  const addTask = (e) => {
    e.preventDefault()
    if (!newTask.trim()) return
    run(() => api.createTask({ title: newTask.trim(), dueDate: date }))
    setNewTask('')
  }

  if (!data) return error ? <div className="error">{error}</div> : <p className="muted">Yükleniyor…</p>

  const { summary, tasks, habits, activeGoals, journal, duePayments } = data
  const scheduledHabits = habits.filter((h) => h.scheduled)
  const isToday = date === today()

  return (
    <>
      <div className="row">
        <div>
          <h1>{isToday ? 'Bugün' : formatDate(date, { day: 'numeric', month: 'long' })}</h1>
          <p className="subtitle">{formatDate(date)}</p>
        </div>
        <div className="spacer" />
        <button className="btn" onClick={() => setDate(addDays(date, -1))}>‹</button>
        {!isToday && <button className="btn" onClick={() => setDate(today())}>Bugün</button>}
        <button className="btn" onClick={() => setDate(addDays(date, 1))}>›</button>
      </div>

      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <div className="stats">
        <Stat value={`${summary.tasksDone}/${summary.tasksTotal}`} label="Görev tamamlandı" />
        <Stat value={`${summary.habitsDone}/${summary.habitsScheduled}`} label="Alışkanlık yapıldı" />
        <Stat value={activeGoals.length} label="Aktif hedef" />
        <Stat value={journal?.mood ? MOODS[journal.mood - 1] : '—'} label="Ruh hali" plain />
      </div>

      {duePayments?.length > 0 && (
        <section className="card due-card" style={{ marginBottom: 20 }}>
          <div className="row" style={{ marginBottom: 4 }}>
            <h2 style={{ margin: 0 }}>💳 Yaklaşan ödemeler</h2>
            <div className="spacer" />
            <a className="muted" href="#finance" onClick={() => { try { localStorage.setItem('financeView', 'payments') } catch { /* yoksay */ } }}>Tümü →</a>
          </div>
          <ul className="list">{duePayments.map((p) => <PaymentRow key={p.id} p={p} run={run} compact />)}</ul>
        </section>
      )}

      <div className="grid-2">
        <section className="card">
          <h2>✓ Görevler</h2>
          <form className="row" onSubmit={addTask} style={{ marginBottom: 8 }}>
            <input style={{ flex: 1 }} placeholder="Yeni görev ekle…" value={newTask} onChange={(e) => setNewTask(e.target.value)} />
            <button className="btn primary">Ekle</button>
          </form>
          <ul className="list">
            {tasks.length === 0 && <li className="empty">Bu gün için görev yok 🎉</li>}
            {tasks.map((t) => (
              <li key={t.id} className={`item ${t.completed ? 'done' : ''}`}>
                <input type="checkbox" className="check" checked={t.completed}
                  onChange={() => run(() => api.setTaskDone(t.id, !t.completed))} />
                <span className="title">{t.title}</span>
                {t.overdue && <span className="tag overdue">Gecikti</span>}
                {t.goalTitle && <span className="tag goal">🎯 {t.goalTitle}</span>}
                {t.priority !== 'MEDIUM' && <span className={`tag ${t.priority}`}>{PRIORITY[t.priority]}</span>}
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>🔁 Alışkanlıklar</h2>
          <ul className="list">
            {scheduledHabits.length === 0 && <li className="empty">Bugüne planlı alışkanlık yok</li>}
            {scheduledHabits.map((h) => (
              <li key={h.id} className="item" style={{ '--hc': h.color || undefined }}>
                <button className={`habit-btn ${h.doneOnDate ? 'done' : ''}`} title="İşaretle"
                  onClick={() => run(() => api.setHabitDone(h.id, date, !h.doneOnDate))}>
                  {h.icon || '•'}
                </button>
                <div className="title">
                  <div>{h.name}</div>
                  <div className="dots" style={{ marginTop: 4 }}>
                    {h.last7Days.map((d) => (
                      <span key={d.date} title={d.date} className={`dot ${d.done ? 'done' : ''} ${d.scheduled ? '' : 'off'}`} />
                    ))}
                  </div>
                </div>
                {h.currentStreak > 0 && <span className="streak">🔥 {h.currentStreak}</span>}
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>🎯 Aktif hedefler</h2>
          {activeGoals.length === 0 && <p className="empty">Henüz hedef yok</p>}
          {activeGoals.map((g) => (
            <div key={g.id} style={{ marginBottom: 14 }}>
              <div className="row" style={{ marginBottom: 6 }}>
                <span>{g.title}</span>
                <div className="spacer" />
                <span className="muted">%{g.progress}</span>
              </div>
              <div className="progress"><div style={{ width: `${g.progress}%` }} /></div>
            </div>
          ))}
        </section>

        <JournalCard date={date} entry={journal} onSaved={load} onError={setError} />
      </div>
    </>
  )
}

function Stat({ value, label, plain }) {
  return (
    <div className="card stat">
      <div className={`value ${plain ? 'plain' : ''}`}>{value}</div>
      <div className="label">{label}</div>
    </div>
  )
}

function JournalCard({ date, entry, onSaved, onError }) {
  const [content, setContent] = useState(entry?.content ?? '')
  const [mood, setMood] = useState(entry?.mood ?? null)

  useEffect(() => {
    setContent(entry?.content ?? '')
    setMood(entry?.mood ?? null)
  }, [entry, date])

  const save = (m = mood) => api.saveJournal(date, { content, mood: m }).then(onSaved).catch((e) => onError(e.message))

  return (
    <section className="card">
      <h2>📓 Günün notu</h2>
      <div className="moods" style={{ marginBottom: 10 }}>
        {MOODS.map((m, i) => (
          <button key={m} className={mood === i + 1 ? 'on' : ''} onClick={() => { setMood(i + 1); save(i + 1) }}>{m}</button>
        ))}
      </div>
      <textarea style={{ minHeight: 100 }} placeholder="Bugün nasıl geçti?" value={content}
        onChange={(e) => setContent(e.target.value)} onBlur={() => content !== (entry?.content ?? '') && save()} />
      <p className="muted">Alandan çıkınca otomatik kaydedilir.</p>
    </section>
  )
}
