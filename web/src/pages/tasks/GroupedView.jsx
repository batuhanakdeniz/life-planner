import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api.js'
import { addDays, today } from '../../util.js'
import TaskItem from '../../components/TaskItem.jsx'
import TaskForm from '../../components/TaskForm.jsx'

export default function GroupedView() {
  const [groups, setGroups] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    const t = today()
    Promise.all([api.tasksForDay(t), api.tasksInRange(addDays(t, 1), addDays(t, 365)), api.somedayTasks()])
      .then(([day, upcoming, someday]) => {
        const tomorrow = addDays(t, 1)
        const weekEnd = addDays(t, 7)
        setGroups([
          { key: 'overdue', title: '⚠️ Gecikmiş', tasks: day.filter((x) => x.overdue) },
          { key: 'today', title: '☀️ Bugün', tasks: day.filter((x) => x.dueDate === t) },
          { key: 'tomorrow', title: '→ Yarın', tasks: upcoming.filter((x) => x.dueDate === tomorrow) },
          { key: 'week', title: '📅 Bu hafta', tasks: upcoming.filter((x) => x.dueDate > tomorrow && x.dueDate <= weekEnd) },
          { key: 'later', title: '🗓 İleride', tasks: upcoming.filter((x) => x.dueDate > weekEnd) },
          { key: 'someday', title: '☁️ Bir gün', tasks: someday },
        ])
      })
      .catch((e) => setError(e.message))
  }, [])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <TaskForm defaultDate={today()} onCreate={(t) => run(() => api.createTask(t))} />
      {groups?.filter((g) => g.tasks.length > 0 || g.key === 'today').map((g) => (
        <section key={g.key} className={`card group ${g.key}`} style={{ marginTop: 16 }}>
          <h2>{g.title} <span className="muted">({g.tasks.length})</span></h2>
          <ul className="list">
            {g.tasks.length === 0 && <li className="empty">Bugün için görev yok</li>}
            {g.tasks.map((t) => <TaskItem key={t.id} task={t} run={run} showDate={g.key !== 'today' && g.key !== 'tomorrow'} />)}
          </ul>
        </section>
      ))}
    </>
  )
}
