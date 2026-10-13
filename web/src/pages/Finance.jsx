import { useState } from 'react'
import PortfolioView from './finance/PortfolioView.jsx'
import TradesView from './finance/TradesView.jsx'
import PaymentsView from './finance/PaymentsView.jsx'

const VIEWS = [
  { id: 'portfolio', label: '◔ Portföy', component: PortfolioView },
  { id: 'trades', label: '⇄ İşlemler', component: TradesView },
  { id: 'payments', label: '🗓 Ödemeler', component: PaymentsView },
]

export default function Finance() {
  const [view, setView] = useState(() => {
    try { return localStorage.getItem('financeView') || 'portfolio' } catch { return 'portfolio' }
  })
  const select = (id) => {
    setView(id)
    try { localStorage.setItem('financeView', id) } catch { /* yoksay */ }
  }
  const View = (VIEWS.find((v) => v.id === view) ?? VIEWS[0]).component
  return (
    <>
      <div className="row">
        <h1>💰 Finans</h1>
        <div className="spacer" />
        <div className="tabs">
          {VIEWS.map((v) => <button key={v.id} className={v.id === view ? 'on' : ''} onClick={() => select(v.id)}>{v.label}</button>)}
        </div>
      </div>
      <p className="subtitle">Yatırımların TL ve dolar bazında, düzenli ödemelerin tek yerde.</p>
      <View />
    </>
  )
}
