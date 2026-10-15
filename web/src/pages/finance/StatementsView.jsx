import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from '../../api.js'
import { formatDate } from '../../util.js'
import { fmtMoney, fmtMoney0, fmtNum } from '../../money.js'
import Donut from '../../components/Donut.jsx'
import FileDrop from '../../components/FileDrop.jsx'

const NEW_COLORS = ['#f59e0b', '#10b981', '#6366f1', '#ec4899', '#14b8a6', '#8b5cf6', '#ef4444', '#84cc16']
const monthYear = (iso) => formatDate(iso, { month: 'long', year: 'numeric' })

export default function StatementsView() {
  const [list, setList] = useState(null)
  const [selected, setSelected] = useState(null)
  const [detail, setDetail] = useState(null)
  const [categories, setCategories] = useState([])
  const [busy, setBusy] = useState(false)
  const [notice, setNotice] = useState(null)
  const [error, setError] = useState(null)

  const loadList = useCallback((selectId) => {
    Promise.all([api.statements(), api.spendingCategories()]).then(([l, c]) => {
      setList(l)
      setCategories(c)
      setSelected((cur) => selectId ?? cur ?? l[0]?.id ?? null)
    }).catch((e) => setError(e.message))
  }, [])
  useEffect(() => loadList(), [loadList])

  const loadDetail = useCallback(() => {
    if (!selected) { setDetail(null); return }
    api.statement(selected).then(setDetail).catch((e) => setError(e.message))
  }, [selected])
  useEffect(loadDetail, [loadDetail])

  const onFile = (file) => {
    setBusy(true)
    setError(null)
    setNotice(null)
    api.uploadStatement(file)
      .then((s) => {
        setNotice(s.duplicate ? 'Bu hesap özeti daha önce yüklenmiş; mevcut kayıt açıldı.' : `${s.bank} hesap özeti okundu: ${s.transactionCount} işlem.`)
        loadList(s.id)
        setSelected(s.id)
      })
      .catch((e) => setError(e.message))
      .finally(() => setBusy(false))
  }

  const refreshAll = () => { loadDetail(); loadList() }

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <div className="statement-top">
        <FileDrop accept=".pdf" label="Hesap özeti PDF'ini buraya bırak ya da tıkla" busy={busy}
          hint="Yapı Kredi (World) · İş Bankası (Maximum, deneysel). Dosya bu bilgisayarda saklanır." onFile={onFile} />
        {list?.length > 0 && (
          <div className="card statement-picker">
            <label className="muted">Hesap özeti</label>
            <select value={selected ?? ''} onChange={(e) => setSelected(Number(e.target.value))}>
              {list.map((s) => (
                <option key={s.id} value={s.id}>{s.bank} ····{s.cardLast4 ?? '????'} · {s.periodEnd ? monthYear(s.periodEnd) : s.fileName}</option>
              ))}
            </select>
            {detail && (
              <button className="btn ghost del" onClick={() => confirm('Bu hesap özeti ve işlemleri silinsin mi? (Dosya da silinir)')
                && api.deleteStatement(detail.summary.id).then(() => { setSelected(null); setDetail(null); loadList(null) }).catch((e) => setError(e.message))}>
                Sil
              </button>
            )}
          </div>
        )}
      </div>
      {notice && <div className="notice" onClick={() => setNotice(null)}>{notice}</div>}

      {list?.length === 0 && !busy && (
        <section className="card empty-state">
          <h2>Henüz hesap özeti yok</h2>
          <p className="muted">Bankanın gönderdiği PDF ekstreyi yükle; işlemler okunup sınıflara ayrılır, taksitlerin listelenir.</p>
        </section>
      )}

      {detail && <StatementDetail d={detail} categories={categories} onChanged={refreshAll} onCategoryCreated={(c) => setCategories((cs) => [...cs, c])} onError={setError} />}
    </>
  )
}

function StatementDetail({ d, categories, onChanged, onCategoryCreated, onError }) {
  const s = d.summary
  const [filter, setFilter] = useState('ALL')
  const [query, setQuery] = useState('')
  const [editing, setEditing] = useState(null) // { tx, categoryId, remember, pattern }
  const catById = useMemo(() => Object.fromEntries(categories.map((c) => [c.id, c])), [categories])

  const spending = d.transactions.filter((t) => t.kind !== 'PAYMENT')
  const shown = d.transactions.filter((t) => {
    if (filter === 'UNCLASSIFIED' && !(t.kind !== 'PAYMENT' && t.categoryId == null)) return false
    if (filter === 'PAYMENTS' && t.kind !== 'PAYMENT') return false
    if (typeof filter === 'number' && t.categoryId !== filter) return false
    if (query && !`${t.merchant} ${t.description}`.toLocaleLowerCase('tr').includes(query.toLocaleLowerCase('tr'))) return false
    return true
  })

  const startEdit = (tx, value) => {
    if (value === '__new') {
      const name = prompt('Yeni sınıfın adı')?.trim()
      if (!name) return
      api.createSpendingCategory({ name, color: NEW_COLORS[categories.length % NEW_COLORS.length] })
        .then((c) => { onCategoryCreated(c); setEditing({ tx, categoryId: c.id, remember: true, pattern: tx.suggestedPattern }) })
        .catch((e) => onError(e.message))
      return
    }
    const categoryId = value === '' ? null : Number(value)
    setEditing({ tx, categoryId, remember: categoryId != null, pattern: tx.rulePattern ?? tx.suggestedPattern })
  }

  const save = () => {
    const { tx, categoryId, remember, pattern } = editing
    api.setTransactionCategory(tx.id, { categoryId, remember: remember && categoryId != null, pattern })
      .then(() => { setEditing(null); onChanged() })
      .catch((e) => onError(e.message))
  }

  const inst = d.installments
  return (
    <>
      {(s.experimental || d.warnings.length > 0) && (
        <div className="warn-box">
          {s.experimental && <div>⚠️ {s.bank} ekstre okuyucusu deneysel; işlemleri kontrol et.</div>}
          {d.warnings.map((w) => <div key={w}>⚠️ {w}</div>)}
        </div>
      )}

      <div className="stats">
        <div className="card stat">
          <div className="label">Dönem borcu</div>
          <div className="value">{fmtMoney0(s.totalDue)}</div>
          <div className={`stat-sub ${d.check?.matches ? 'pos' : 'warn'}`}>
            {d.check ? (d.check.matches ? '✓ İşlemlerin toplamı ekstreyle tutuyor' : `Okunan: ${fmtMoney(d.check.parsed)}`) : ''}
          </div>
        </div>
        <div className="card stat">
          <div className="label">Son ödeme</div>
          <div className="value plain">{s.dueDate ? formatDate(s.dueDate, { day: 'numeric', month: 'long' }) : '—'}</div>
          <div className="stat-sub">Kesim: {s.periodEnd ? formatDate(s.periodEnd, { day: 'numeric', month: 'long' }) : '—'}</div>
        </div>
        <div className="card stat">
          <div className="label">Taksitler (gelecek ay)</div>
          <div className="value plain">{fmtMoney0(inst.nextMonthTotal)}</div>
          <div className="stat-sub">{inst.active} aktif taksit · kalan {fmtMoney0(inst.remainingTotal)}</div>
        </div>
        <div className="card stat clickable" onClick={() => setFilter('UNCLASSIFIED')} title="Sınıflandırılmamışları göster">
          <div className="label">Sınıflandırılmamış</div>
          <div className={`value plain ${s.unclassifiedCount ? 'warn' : 'pos'}`}>{s.unclassifiedCount}</div>
          <div className="stat-sub">{spending.length} harcama işleminden</div>
        </div>
      </div>

      <div className="fin-grid statement-grid">
        <section className="card donut-card">
          <h2>Harcamalar nereye gitti?</h2>
          <Donut title={fmtMoney0(s.spending)} subtitle={`${spending.length} işlem`}
            slices={d.breakdown.filter((b) => b.amount > 0).map((b) => ({ key: String(b.categoryId), value: b.amount, color: b.color, label: `${b.name} %${b.percent}` }))} />
          <ul className="legend">
            {d.breakdown.map((b) => (
              <li key={String(b.categoryId)} className={`clickable ${filter === (b.categoryId ?? 'UNCLASSIFIED') ? 'active' : ''}`}
                onClick={() => setFilter(b.categoryId ?? 'UNCLASSIFIED')}>
                <span className="swatch-dot" style={{ background: b.color }} />
                <span className="grow">{b.name} <span className="muted">({b.count})</span></span>
                <span className="muted">{fmtMoney0(b.amount)}</span>
                <strong>%{fmtNum(b.percent, 1)}</strong>
              </li>
            ))}
          </ul>
        </section>

        <section className="card">
          <h2>Taksitler</h2>
          {inst.items.length === 0 && <p className="empty">Taksitli işlem yok</p>}
          <table className="fin-table">
            <thead><tr><th>İşlem</th><th className="num">Taksit</th><th className="num">Aylık</th><th className="num">Kalan</th><th className="num">Bitiş</th></tr></thead>
            <tbody>
              {inst.items.map((i) => (
                <tr key={i.transactionId} className={i.remainingInstallments === 0 ? 'done-row' : ''}>
                  <td>
                    <div>{i.merchant}</div>
                    <div className="muted">{formatDate(i.purchaseDate, { day: 'numeric', month: 'short', year: 'numeric' })}{i.purchaseTotal ? ` · toplam ${fmtMoney0(i.purchaseTotal)}` : ''}</div>
                  </td>
                  <td className="num">{i.installmentNo}/{i.installmentCount}</td>
                  <td className="num">{fmtMoney(i.monthly)}</td>
                  <td className="num">{i.remainingInstallments > 0 ? <>{fmtMoney0(i.remainingAmount)}<div className="muted">{i.remainingInstallments} taksit</div></> : <span className="pos">bitti</span>}</td>
                  <td className="num">{i.remainingInstallments > 0 && i.endsIn ? formatDate(i.endsIn, { month: 'short', year: 'numeric' }) : '—'}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      </div>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="row" style={{ marginBottom: 8 }}>
          <h2 style={{ margin: 0 }}>İşlemler</h2>
          <div className="spacer" />
          <input placeholder="Ara…" value={query} onChange={(e) => setQuery(e.target.value)} style={{ width: 160 }} />
          <select value={String(filter)} onChange={(e) => { const v = e.target.value; setFilter(v === 'ALL' || v === 'UNCLASSIFIED' || v === 'PAYMENTS' ? v : Number(v)) }}>
            <option value="ALL">Tümü ({d.transactions.length})</option>
            <option value="UNCLASSIFIED">Sınıflandırılmamış ({s.unclassifiedCount})</option>
            <option value="PAYMENTS">Ödemeler</option>
            {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
          </select>
        </div>
        <div className="table-wrap">
          <table className="fin-table tx-table">
            <thead><tr><th>Tarih</th><th>İş yeri</th><th className="num">Tutar</th><th>Sınıf</th></tr></thead>
            <tbody>
              {shown.length === 0 && <tr><td colSpan={4} className="empty">Bu filtrede işlem yok</td></tr>}
              {shown.map((t) => (
                <TxRow key={t.id} t={t} categories={categories} cat={catById[t.categoryId]} editing={editing?.tx.id === t.id ? editing : null}
                  onPick={(v) => startEdit(t, v)} onEdit={setEditing} onSave={save} onCancel={() => setEditing(null)} />
              ))}
            </tbody>
          </table>
        </div>
      </section>

      <RulesPanel categories={categories} onChanged={onChanged} onError={onError} />
    </>
  )
}

function TxRow({ t, categories, cat, editing, onPick, onEdit, onSave, onCancel }) {
  const isPayment = t.kind === 'PAYMENT'
  return (
    <>
      <tr className={editing ? 'selected' : ''}>
        <td className="nowrap">{formatDate(t.date, { day: 'numeric', month: 'short' })}</td>
        <td title={t.description}>
          <div>{t.merchant}</div>
          <div className="muted">
            {t.installmentNo ? `${t.installmentNo}/${t.installmentCount} taksit · ` : ''}
            {t.foreignCurrency ? `${fmtNum(t.foreignAmount, 2)} ${t.foreignCurrency} · ` : ''}
            {t.kind === 'FEE' ? 'faiz/ücret · ' : t.kind === 'REFUND' ? 'iade · ' : ''}
            {t.categorySource === 'RULE' && t.rulePattern ? `kural: "${t.rulePattern}"` : t.categorySource === 'MANUAL' ? 'elle seçildi' : ''}
          </div>
        </td>
        <td className={`num ${t.amount < 0 ? 'pos' : ''}`}>{fmtMoney(t.amount)}</td>
        <td>
          {isPayment ? <span className="muted">Ödeme</span> : (
            <div className="row" style={{ gap: 6, flexWrap: 'nowrap' }}>
              <span className="swatch-dot" style={{ background: cat?.color ?? '#334155' }} />
              <select value={editing ? String(editing.categoryId ?? '') : String(t.categoryId ?? '')} onChange={(e) => onPick(e.target.value)}>
                <option value="">— Sınıflandırılmamış —</option>
                {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                <option value="__new">+ Yeni sınıf…</option>
              </select>
            </div>
          )}
        </td>
      </tr>
      {editing && (
        <tr className="edit-row">
          <td colSpan={4}>
            <div className="row">
              {editing.categoryId != null && (
                <label className="row" style={{ gap: 6 }}>
                  <input type="checkbox" checked={editing.remember} onChange={(e) => onEdit({ ...editing, remember: e.target.checked })} />
                  Bundan sonra açıklamasında
                  <input value={editing.pattern ?? ''} disabled={!editing.remember} onChange={(e) => onEdit({ ...editing, pattern: e.target.value })}
                    style={{ width: 180 }} />
                  geçen işlemler de bu sınıfa girsin
                </label>
              )}
              <div className="spacer" />
              <button className="btn primary" onClick={onSave}>Kaydet</button>
              <button className="btn" onClick={onCancel}>Vazgeç</button>
            </div>
          </td>
        </tr>
      )}
    </>
  )
}

function RulesPanel({ categories, onChanged, onError }) {
  const [open, setOpen] = useState(false)
  const [rules, setRules] = useState([])
  const load = useCallback(() => { api.categoryRules().then(setRules).catch((e) => onError(e.message)) }, [onError])
  useEffect(() => { if (open) load() }, [open, load])

  const run = (p) => p.then(() => { load(); onChanged() }).catch((e) => onError(e.message))

  return (
    <section className="card" style={{ marginTop: 16 }}>
      <button className="link-btn" onClick={() => setOpen(!open)}>{open ? '▾' : '▸'} Sınıflandırma kuralların</button>
      {open && (
        <>
          <p className="muted">Bir işlemin sınıfını değiştirip "bundan sonra" dediğinde oluşan kurallar. Hazır kurallardan önce uygulanır.</p>
          {rules.length === 0 && <p className="empty">Henüz kuralın yok</p>}
          <table className="fin-table">
            <tbody>
              {rules.map((r) => (
                <tr key={r.id}>
                  <td>Açıklamasında <code>{r.pattern}</code> geçen</td>
                  <td>
                    <select value={r.categoryId} onChange={(e) => run(api.updateCategoryRule(r.id, { pattern: r.pattern, categoryId: Number(e.target.value) }))}>
                      {categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}
                    </select>
                  </td>
                  <td className="num muted">{r.matches} işlem</td>
                  <td className="num"><button className="btn ghost del" onClick={() => confirm(`"${r.pattern}" kuralı silinsin mi?`) && run(api.deleteCategoryRule(r.id))}>✕</button></td>
                </tr>
              ))}
            </tbody>
          </table>
        </>
      )}
    </section>
  )
}
