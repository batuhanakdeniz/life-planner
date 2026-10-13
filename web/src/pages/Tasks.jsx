import { useState } from 'react'
import WeeklyView from './tasks/WeeklyView.jsx'
import GroupedView from './tasks/GroupedView.jsx'
import KanbanView from './tasks/KanbanView.jsx'

const VIEWS = [
  { id: 'week', label: '📅 Haftalık', component: WeeklyView },
  { id: 'list', label: '☰ Liste', component: GroupedView },
  { id: 'board', label: '▦ Kanban', component: KanbanView },
]

export default function Tasks() {
  const [view, setView] = useState(() => {
    try { return localStorage.getItem('taskView') || 'week' } catch { return 'week' }
  })
  const select = (id) => {
    setView(id)
    try { localStorage.setItem('taskView', id) } catch { /* yoksay */ }
  }
  const View = (VIEWS.find((v) => v.id === view) ?? VIEWS[0]).component

  return (
    <>
      <div className="row">
        <h1>✓ Görevler</h1>
        <div className="spacer" />
        <div className="tabs">
          {VIEWS.map((v) => (
            <button key={v.id} className={v.id === view ? 'on' : ''} onClick={() => select(v.id)}>{v.label}</button>
          ))}
        </div>
      </div>
      <p className="subtitle">Görevlerini gün, grup ya da durum bazında yönet.</p>
      <View />
    </>
  )
}
