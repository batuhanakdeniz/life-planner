import { useEffect, useState } from 'react'
import Today from './pages/Today.jsx'
import Tasks from './pages/Tasks.jsx'
import Habits from './pages/Habits.jsx'
import Goals from './pages/Goals.jsx'
import Journal from './pages/Journal.jsx'
import Finance from './pages/Finance.jsx'
import Bulletin from './pages/Bulletin.jsx'
import Projects from './pages/Projects.jsx'

const PAGES = [
  { id: 'today', icon: '☀️', label: 'Bugün', component: Today },
  { id: 'tasks', icon: '✓', label: 'Görevler', component: Tasks },
  { id: 'habits', icon: '🔁', label: 'Alışkanlıklar', component: Habits },
  { id: 'goals', icon: '🎯', label: 'Hedefler', component: Goals },
  { id: 'journal', icon: '📓', label: 'Günlük', component: Journal },
  { id: 'finance', icon: '💰', label: 'Finans', component: Finance },
  { id: 'bulletin', icon: '📰', label: 'Bülten', component: Bulletin },
  { id: 'projects', icon: '🚀', label: 'Projeler', component: Projects },
]

function useTheme() {
  const [theme, setTheme] = useState(() => {
    try { return localStorage.getItem('theme') } catch { return null }
  })
  useEffect(() => {
    if (theme) document.documentElement.dataset.theme = theme
    else delete document.documentElement.dataset.theme
    try { theme ? localStorage.setItem('theme', theme) : localStorage.removeItem('theme') } catch { /* yoksay */ }
  }, [theme])
  const isDark = theme ? theme === 'dark' : matchMedia('(prefers-color-scheme: dark)').matches
  return [isDark, () => setTheme(isDark ? 'light' : 'dark')]
}

export default function App() {
  const [page, setPage] = useState(() => location.hash.slice(1) || 'today')
  const [isDark, toggleTheme] = useTheme()

  useEffect(() => {
    const onHash = () => setPage(location.hash.slice(1) || 'today')
    addEventListener('hashchange', onHash)
    return () => removeEventListener('hashchange', onHash)
  }, [])

  const current = PAGES.find((p) => p.id === page) ?? PAGES[0]
  const Page = current.component

  return (
    <div className="layout">
      <aside className="sidebar">
        <div className="brand">🌱 Hayat Planlayıcı</div>
        <nav className="nav">
          {PAGES.map((p) => (
            <button key={p.id} className={p.id === current.id ? 'active' : ''} onClick={() => (location.hash = p.id)}>
              <span>{p.icon}</span> {p.label}
            </button>
          ))}
        </nav>
        <button className="btn ghost theme-toggle" onClick={toggleTheme}>
          {isDark ? '☀️ Açık tema' : '🌙 Koyu tema'}
        </button>
      </aside>
      <main>
        {Page ? <Page /> : (
          <>
            <h1>{current.icon} {current.label}</h1>
            <p className="subtitle">Bu sayfanın tasarımını birlikte belirleyeceğiz.</p>
          </>
        )}
      </main>
    </div>
  )
}
