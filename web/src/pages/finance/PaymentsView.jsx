import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api.js'
import { formatDate, today } from '../../util.js'
import { currencySymbol, fmtMoney, fmtMoney0, FREQUENCY, parseNum } from '../../money.js'

export const dueLabel = (days) =>
  days < 0 ? { text: `${-days} gün gecikti`, cls: 'overdue' }
    : days === 0 ? { text: 'Bugün', cls: 'HIGH' }
      : days === 1 ? { text: 'Yarın', cls: 'goal' }
        : { text: `${days} gün`, cls: '' }

export default function PaymentsView() {
  const [data, setData] = useState(null)
  const [editing, setEditing] = useState(null) // null | 'new' | payment
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    api.payments().then(setData).catch((e) => setError(e.message))
  }, [])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  if (!data) return error ? <div className="error">{error}</div> : <p className="muted">Yükleniyor…</p>

  const active = data.payments.filter((p) => p.active)
  const passive = data.payments.filter((p) => !p.active)

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <div className="stats">
        <div className="card stat">
          <div className="label">Aylık sabit yük</div>
          <div className="value">{fmtMoney0(data.monthlyTotalTry)}</div>
          {data.missingRates.length > 0 && <div className="stat-sub warn">{data.missingRates.join(', ')} kuru girilmediği için bazı ödemeler hariç</div>}
        </div>
        <div className="card stat">
          <div className="label">Bu ay ödenen</div>
          <div className="value plain">{fmtMoney0(data.paidThisMonthTry)}</div>
        </div>
        <div className="card stat">
          <div className="label">Yaklaşan (7 gün)</div>
          <div className="value plain">{active.filter((p) => p.daysUntilDue <= 7).length}</div>
        </div>
      </div>

      {editing && (
        <PaymentForm key={editing === 'new' ? 'new' : editing.id} payment={editing === 'new' ? null : editing} onCancel={() => setEditing(null)}
          onSave={(body) => run(() => (editing === 'new' ? api.createPayment(body) : api.updatePayment(editing.id, body))).then(() => setEditing(null))} />
      )}

      <section className="card">
        <div className="row" style={{ marginBottom: 8 }}>
          <h2 style={{ margin: 0 }}>Düzenli ödemeler</h2>
          <div className="spacer" />
          <button className="btn primary" onClick={() => setEditing('new')}>+ Ödeme ekle</button>
        </div>
        <ul className="list">
          {active.length === 0 && <li className="empty">Henüz düzenli ödeme yok. Kira, fatura, abonelik ekleyebilirsin.</li>}
          {active.map((p) => <PaymentRow key={p.id} p={p} run={run} onEdit={() => setEditing(p)} />)}
        </ul>
        {passive.length > 0 && (
          <>
            <h2 style={{ marginTop: 16 }} className="muted">Duraklatılmış</h2>
            <ul className="list">{passive.map((p) => <PaymentRow key={p.id} p={p} run={run} onEdit={() => setEditing(p)} />)}</ul>
          </>
        )}
      </section>

      <section className="card" style={{ marginTop: 16 }}>
        <h2>Son ödemeler</h2>
        <ul className="list">
          {data.recent.length === 0 && <li className="empty">Henüz ödeme kaydı yok</li>}
          {data.recent.map((l) => (
            <li key={l.id} className="item">
              <span className="title">{l.paymentName} <span className="muted">· {formatDate(l.dueDate, { day: 'numeric', month: 'long' })} vadesi</span></span>
              <span className="muted">{formatDate(l.paidDate, { day: 'numeric', month: 'short' })}</span>
              <strong>{fmtMoney(l.amount, l.currency)}</strong>
              <button className="btn ghost del" title="Geri al" onClick={() => confirm('Bu ödeme kaydı geri alınsın mı?') && run(() => api.undoPayment(l.id))}>↺</button>
            </li>
          ))}
        </ul>
      </section>
    </>
  )
}

export function PaymentRow({ p, run, onEdit, compact = false }) {
  const due = dueLabel(p.daysUntilDue)
  return (
    <li className={`item ${p.active ? '' : 'done'}`}>
      <div className="title">
        <div>{p.name} {!compact && p.category && <span className="tag">{p.category}</span>}</div>
        {!compact && <div className="muted">{FREQUENCY[p.frequency]} · sonraki: {formatDate(p.nextDueDate, { day: 'numeric', month: 'long' })}{p.lastPaidDate && ` · son ödeme ${formatDate(p.lastPaidDate, { day: 'numeric', month: 'short' })}`}</div>}
      </div>
      {p.active && <span className={`tag ${due.cls}`}>{due.text}</span>}
      <strong className="nowrap">{fmtMoney(p.amount, p.currency)}</strong>
      {p.active && <button className="btn pay-btn" onClick={() => run(() => api.payPayment(p.id))} title="Bu vadeyi ödendi olarak işaretle">✓ Ödendi</button>}
      {onEdit && <button className="btn ghost" title="Düzenle" onClick={onEdit}>✎</button>}
      {onEdit && <button className="btn ghost del" title="Sil" onClick={() => confirm(`"${p.name}" ve ödeme geçmişi silinsin mi?`) && run(() => api.deletePayment(p.id))}>✕</button>}
    </li>
  )
}

function PaymentForm({ payment, onSave, onCancel }) {
  const [f, setF] = useState({
    name: payment?.name ?? '', category: payment?.category ?? '',
    amount: payment ? String(payment.amount).replace('.', ',') : '', currency: payment?.currency ?? 'TRY',
    frequency: payment?.frequency ?? 'MONTHLY', nextDueDate: payment?.nextDueDate ?? today(),
    active: payment?.active ?? true, note: payment?.note ?? '',
  })
  const set = (k) => (e) => setF({ ...f, [k]: e.target.type === 'checkbox' ? e.target.checked : e.target.value })
  const submit = (e) => {
    e.preventDefault()
    const amount = parseNum(f.amount)
    if (!f.name.trim() || !amount || amount <= 0) return
    onSave({ ...f, name: f.name.trim(), amount, category: f.category || null, note: f.note || null })
  }
  return (
    <form className="card form" onSubmit={submit} style={{ marginBottom: 16 }}>
      <h2>{payment ? 'Ödemeyi düzenle' : 'Yeni düzenli ödeme'}</h2>
      <div className="row">
        <input style={{ flex: 2, minWidth: 180 }} placeholder="Ad (ör. Kira, Netflix)" value={f.name} onChange={set('name')} autoFocus />
        <input style={{ flex: 1, minWidth: 120 }} placeholder="Kategori (ör. Fatura)" value={f.category} onChange={set('category')} list="pay-cats" />
        <datalist id="pay-cats">{['Ev', 'Fatura', 'Abonelik', 'Sigorta', 'Kredi', 'Eğitim', 'Ulaşım'].map((c) => <option key={c} value={c} />)}</datalist>
      </div>
      <div className="row">
        <input inputMode="decimal" placeholder="Tutar" value={f.amount} onChange={set('amount')} style={{ width: 140 }} />
        <select value={f.currency} onChange={set('currency')}>
          {['TRY', 'USD', 'EUR'].map((c) => <option key={c} value={c}>{currencySymbol(c)} {c}</option>)}
        </select>
        <select value={f.frequency} onChange={set('frequency')}>
          {Object.entries(FREQUENCY).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
        </select>
        <label className="muted row">Sonraki ödeme <input type="date" value={f.nextDueDate} onChange={set('nextDueDate')} /></label>
        {payment && <label className="muted row"><input type="checkbox" checked={f.active} onChange={set('active')} /> Aktif</label>}
      </div>
      <input placeholder="Not (isteğe bağlı)" value={f.note} onChange={set('note')} />
      <div className="row">
        <button className="btn primary">Kaydet</button>
        <button type="button" className="btn" onClick={onCancel}>Vazgeç</button>
      </div>
    </form>
  )
}
