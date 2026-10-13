import { api } from '../api.js'
import { formatDate, PRIORITY } from '../util.js'

/** Tek görev satırı. `run` bir API çağrısını çalıştırıp listeyi yeniler. */
export default function TaskItem({ task: t, run, showDate = false, compact = false }) {
  return (
    <li className={`item ${t.completed ? 'done' : ''} ${compact ? 'compact' : ''}`}>
      <input type="checkbox" className="check" checked={t.completed}
        onChange={() => run(() => api.setTaskDone(t.id, !t.completed))} />
      <span className="title">{t.title}</span>
      {showDate && t.dueDate && <span className="tag">{formatDate(t.dueDate, { day: 'numeric', month: 'short' })}</span>}
      {t.overdue && <span className="tag overdue">Gecikti</span>}
      {!compact && t.goalTitle && <span className="tag goal">🎯 {t.goalTitle}</span>}
      {t.priority !== 'MEDIUM' && <span className={`tag ${t.priority}`}>{compact ? (t.priority === 'HIGH' ? '!' : '↓') : PRIORITY[t.priority]}</span>}
      <button className="btn ghost del" title="Sil" onClick={() => confirm(`"${t.title}" silinsin mi?`) && run(() => api.deleteTask(t.id))}>✕</button>
    </li>
  )
}
