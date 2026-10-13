import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api.js'
import { formatDate, PRIORITY, TASK_STATUS } from '../../util.js'
import TaskForm from '../../components/TaskForm.jsx'

export default function KanbanView() {
  const [tasks, setTasks] = useState([])
  const [error, setError] = useState(null)
  const [dragOver, setDragOver] = useState(null)

  const load = useCallback(() => {
    api.taskBoard().then(setTasks).catch((e) => setError(e.message))
  }, [])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  const drop = (status) => (e) => {
    e.preventDefault()
    setDragOver(null)
    const id = Number(e.dataTransfer.getData('text/plain'))
    const task = tasks.find((t) => t.id === id)
    if (!task || task.status === status) return
    // İyimser güncelleme: kart hemen taşınır, sunucu yanıtıyla doğrulanır
    setTasks((ts) => ts.map((t) => (t.id === id ? { ...t, status } : t)))
    run(() => api.setTaskStatus(id, status))
  }

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <TaskForm onCreate={(t) => run(() => api.createTask(t))} />
      <p className="muted">Kartları sürükleyerek sütunlar arasında taşı. "Bitti" sütununda son 7 gün görünür.</p>
      <div className="board">
        {Object.entries(TASK_STATUS).map(([status, label]) => {
          const col = tasks.filter((t) => t.status === status)
          return (
            <div key={status} className={`board-col ${status} ${dragOver === status ? 'over' : ''}`}
              onDragOver={(e) => { e.preventDefault(); setDragOver(status) }}
              onDragLeave={() => setDragOver(null)}
              onDrop={drop(status)}>
              <h2>{label} <span className="muted">{col.length}</span></h2>
              {col.map((t) => (
                <div key={t.id} className={`kcard ${t.completed ? 'done' : ''}`} draggable
                  onDragStart={(e) => e.dataTransfer.setData('text/plain', String(t.id))}>
                  <div className="row">
                    <span className="title">{t.title}</span>
                    <div className="spacer" />
                    <button className="btn ghost del" onClick={() => confirm(`"${t.title}" silinsin mi?`) && run(() => api.deleteTask(t.id))}>✕</button>
                  </div>
                  <div className="row" style={{ marginTop: 8 }}>
                    {t.dueDate && <span className={`tag ${t.overdue ? 'overdue' : ''}`}>📅 {formatDate(t.dueDate, { day: 'numeric', month: 'short' })}</span>}
                    {t.priority !== 'MEDIUM' && <span className={`tag ${t.priority}`}>{PRIORITY[t.priority]}</span>}
                    {t.goalTitle && <span className="tag goal">🎯 {t.goalTitle}</span>}
                  </div>
                </div>
              ))}
              {col.length === 0 && <p className="empty">Buraya sürükle</p>}
            </div>
          )
        })}
      </div>
    </>
  )
}
