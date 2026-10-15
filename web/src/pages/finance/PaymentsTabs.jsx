import { useState } from 'react'
import PaymentsView from './PaymentsView.jsx'
import StatementsView from './StatementsView.jsx'

/** Ödemeler sekmesi: düzenli ödemeler ve hesap özetleri (kredi kartı ekstreleri). */
export default function PaymentsTabs() {
  const [tab, setTab] = useState(() => {
    try { return localStorage.getItem('paymentsTab') || 'recurring' } catch { return 'recurring' }
  })
  const select = (t) => {
    setTab(t)
    try { localStorage.setItem('paymentsTab', t) } catch { /* yoksay */ }
  }
  return (
    <>
      <div className="tabs sub-tabs">
        <button className={tab === 'recurring' ? 'on' : ''} onClick={() => select('recurring')}>Düzenli</button>
        <button className={tab === 'statements' ? 'on' : ''} onClick={() => select('statements')}>Hesap özetleri</button>
      </div>
      {tab === 'recurring' ? <PaymentsView /> : <StatementsView />}
    </>
  )
}
