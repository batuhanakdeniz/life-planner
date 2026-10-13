import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { PRIORITY } from '../util.js'

/** Detaylı görev ekleme formu: başlık, tarih, öncelik, hedef. */
export default function TaskForm({ defaultDate = '', onCreate }) {
  const [title, setTitle] = useState('')
  const [dueDate, setDueDate] = useState(defaultDate)
  const [priority, setPriority] = useState('MEDIUM')
  const [goalId, setGoalId] = useState('')
  const [goals, setGoals] = useState([])

  useEffect(() => { api.goals('ACTIVE').then(setGoals).catch(() => {}) }, [])
  useEffect(() => setDueDate(defaultDate), [defaultDate])

  const submit = (e) => {
    e.preventDefault()
    if (!title.trim()) return
    onCreate({ title: title.trim(), dueDate: dueDate || null, priority, goalId: goalId ? Number(goalId) : null })
    setTitle('')
  }

  return (
    <form className="card row task-form" onSubmit={submit}>
      <input style={{ flex: 2, minWidth: 180 }} placeholder="Yeni görev…" value={title} onChange={(e) => setTitle(e.target.value)} />
      <input type="date" value={dueDate} onChange={(e) => setDueDate(e.target.value)} title="Boş bırakılırsa 'Bir gün' listesine eklenir" />
      <select value={priority} onChange={(e) => setPriority(e.target.value)}>
        {Object.entries(PRIORITY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
      </select>
      <select value={goalId} onChange={(e) => setGoalId(e.target.value)}>
        <option value="">Hedef yok</option>
        {goals.map((g) => <option key={g.id} value={g.id}>🎯 {g.title}</option>)}
      </select>
      <button className="btn primary">Ekle</button>
    </form>
  )
}
