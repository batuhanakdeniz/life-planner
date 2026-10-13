import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api.js'
import { addDays, formatDate, startOfWeek, today } from '../../util.js'
import TaskItem from '../../components/TaskItem.jsx'
import TaskForm from '../../components/TaskForm.jsx'

export default function WeeklyView() {
  const [monday, setMonday] = useState(startOfWeek(today()))
  const [tasks, setTasks] = useState([])
  const [someday, setSomeday] = useState([])
  const [error, setError] = useState(null)
  const days = Array.from({ length: 7 }, (_, i) => addDays(monday, i))

  const load = useCallback(() => {
    Promise.all([api.tasksInRange(monday, addDays(monday, 6)), api.somedayTasks()])
      .then(([w, s]) => { setTasks(w); setSomeday(s) })
      .catch((e) => setError(e.message))
  }, [monday])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <TaskForm onCreate={(t) => run(() => api.createTask(t))} />
      <div className="row" style={{ margin: '16px 0 10px' }}>
        <button className="btn" onClick={() => setMonday(addDays(monday, -7))}>‹</button>
        <strong>{formatDate(monday, { day: 'numeric', month: 'long' })} – {formatDate(days[6], { day: 'numeric', month: 'long' })}</strong>
        <button className="btn" onClick={() => setMonday(addDays(monday, 7))}>›</button>
        {monday !== startOfWeek(today()) && <button className="btn" onClick={() => setMonday(startOfWeek(today()))}>Bu hafta</button>}
      </div>
      <div className="week">
        {days.map((d) => (
          <DayColumn key={d} date={d} tasks={tasks.filter((t) => t.dueDate === d)} run={run} />
        ))}
      </div>
      <section className="card" style={{ marginTop: 20 }}>
        <h2>☁️ Bir gün</h2>
        <p className="muted" style={{ marginTop: -6 }}>Tarihi olmayan görevler</p>
        <ul className="list">
          {someday.length === 0 && <li className="empty">Boş</li>}
          {someday.map((t) => <TaskItem key={t.id} task={t} run={run} />)}
        </ul>
      </section>
    </>
  )
}

function DayColumn({ date, tasks, run }) {
  const [title, setTitle] = useState('')
  const isToday = date === today()
  const add = (e) => {
    e.preventDefault()
    if (!title.trim()) return
    run(() => api.createTask({ title: title.trim(), dueDate: date }))
    setTitle('')
  }
  return (
    <div className={`card day-col ${isToday ? 'today' : ''}`}>
      <div className="day-head">
        <span>{formatDate(date, { weekday: 'short' })}</span>
        <span className="day-num">{formatDate(date, { day: 'numeric' })}</span>
      </div>
      <ul className="list">
        {tasks.map((t) => <TaskItem key={t.id} task={t} run={run} compact />)}
      </ul>
      <form onSubmit={add}>
        <input className="quick-add" placeholder="+ Ekle" value={title} onChange={(e) => setTitle(e.target.value)} />
      </form>
    </div>
  )
}
