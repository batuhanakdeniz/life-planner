import { useCallback, useEffect, useState } from 'react'
import { api } from '../../api.js'
import Donut from '../../components/Donut.jsx'
import {
  ASSET_COLOR, ASSET_ICON, ASSET_PRESETS, ASSET_TYPE, currencySymbol, fmtMoney, fmtMoney0, fmtNum, fmtPct,
  fmtSigned, isStale, parseNum, pnlClass, PRICE_SOURCE, timeAgo,
} from '../../money.js'

export default function PortfolioView() {
  const [data, setData] = useState(null)
  const [assets, setAssets] = useState([])
  const [rates, setRates] = useState([])
  const [showForm, setShowForm] = useState(false)
  const [status, setStatus] = useState(null)
  const [refreshing, setRefreshing] = useState(false)
  const [editingSource, setEditingSource] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    Promise.all([api.portfolio(), api.assets(), api.rates(), api.priceStatus()])
      .then(([p, a, r, st]) => { setData(p); setAssets(a); setRates(r); setStatus(st) })
      .catch((e) => setError(e.message))
  }, [])

  const refreshPrices = () => {
    setRefreshing(true)
    api.refreshPrices().then(load).catch((e) => setError(e.message)).finally(() => setRefreshing(false))
  }
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))

  if (!data) return error ? <div className="error">{error}</div> : <p className="muted">Yükleniyor…</p>

  const { totals: t, allocation, holdings } = data
  const open = holdings.filter((h) => !h.closed)
  const closed = holdings.filter((h) => h.closed)
  const rate = (c) => rates.find((r) => r.currency === c)
  const unused = assets.filter((a) => !a.hasTrades)

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      <PriceStatusBar status={status} refreshing={refreshing} onRefresh={refreshPrices} />

      <div className="stats fin-stats">
        <div className="card stat">
          <div className="label">Toplam değer</div>
          <div className="value">{fmtMoney0(t.valueTry)}</div>
          <div className="stat-sub">{fmtMoney0(t.valueUsd, 'USD')}</div>
        </div>
        <div className="card stat">
          <div className="label">Gerçekleşmemiş kâr/zarar</div>
          <div className={`value plain ${pnlClass(t.unrealizedTry)}`}>{fmtSigned(t.unrealizedTry, 'TRY', 0)} <small>{fmtPct(t.unrealizedPctTry)}</small></div>
          <div className={`stat-sub ${pnlClass(t.unrealizedUsd)}`}>{fmtSigned(t.unrealizedUsd, 'USD', 0)} <small>{fmtPct(t.unrealizedPctUsd)}</small></div>
        </div>
        <div className="card stat">
          <div className="label">Gerçekleşen kâr/zarar</div>
          <div className={`value plain ${pnlClass(t.realizedTry)}`}>{fmtSigned(t.realizedTry, 'TRY', 0)}</div>
          <div className={`stat-sub ${pnlClass(t.realizedUsd)}`}>{fmtSigned(t.realizedUsd, 'USD', 0)}</div>
        </div>
        <div className="card stat">
          <div className="label">Kurlar</div>
          <RateEditor currency="USD" rate={rate('USD')} fallback={data.rateIsFallback ? data.usdTry : null} onSave={(v) => run(() => api.setRate('USD', v))} />
          <RateEditor currency="EUR" rate={rate('EUR')} onSave={(v) => run(() => api.setRate('EUR', v))} />
        </div>
      </div>

      {open.length === 0 ? (
        <section className="card empty-state">
          <h2>Portföyün boş</h2>
          <p className="muted">Önce bir varlık ekle (ör. Gram altın), sonra "İşlemler" sekmesinden alımlarını gir.</p>
          <button className="btn primary" onClick={() => setShowForm(true)}>+ Varlık ekle</button>
        </section>
      ) : (
        <div className="fin-grid">
          <section className="card donut-card">
            <h2>Dağılım</h2>
            <Donut title={fmtMoney0(t.valueTry)} subtitle={fmtMoney0(t.valueUsd, 'USD')}
              slices={allocation.map((a) => ({ key: a.type, value: a.valueTry, color: ASSET_COLOR[a.type], label: `${ASSET_TYPE[a.type]} %${a.percent}` }))} />
            <ul className="legend">
              {allocation.map((a) => (
                <li key={a.type}>
                  <span className="swatch-dot" style={{ background: ASSET_COLOR[a.type] }} />
                  <span className="grow">{ASSET_ICON[a.type]} {ASSET_TYPE[a.type]}</span>
                  <span className="muted">{fmtMoney0(a.valueTry)}</span>
                  <strong>%{fmtNum(a.percent, 1)}</strong>
                </li>
              ))}
            </ul>
          </section>

          <section className="card holdings-card">
            <div className="row" style={{ marginBottom: 8 }}>
              <h2 style={{ margin: 0 }}>Varlıklar</h2>
              <div className="spacer" />
              <button className="btn" onClick={() => setShowForm(!showForm)}>+ Varlık</button>
            </div>
            <div className="table-wrap">
              <table className="fin-table">
                <thead>
                  <tr><th>Varlık</th><th className="num">Miktar</th><th className="num">Ort. maliyet</th><th className="num">Güncel fiyat</th><th className="num">Değer</th><th className="num">Kâr/zarar</th><th className="num">Ağırlık</th></tr>
                </thead>
                <tbody>
                  {open.map((h) => (
                    <tr key={h.assetId}>
                      <td>
                        <div className="asset-name"><span className="swatch-dot" style={{ background: ASSET_COLOR[h.type] }} />{h.name}</div>
                        <div className="muted">{h.symbol}</div>
                      </td>
                      <td className="num">{fmtNum(h.quantity)} <span className="muted">{h.unit}</span></td>
                      <td className="num">{fmtMoney(h.avgCost, h.priceCurrency)}</td>
                      <td className="num">
                        <PriceCell holding={h} onSave={(v) => run(() => api.setAssetPrice(h.assetId, v))} />
                        <SourceTag holding={h} onClick={() => setEditingSource(editingSource === h.assetId ? null : h.assetId)} />
                        {editingSource === h.assetId && (
                          <SourceEditor asset={assets.find((a) => a.id === h.assetId)} onCancel={() => setEditingSource(null)}
                            onSave={(src, sym) => run(() => api.setAssetSource(h.assetId, src, sym)).then(() => { setEditingSource(null); refreshPrices() })} />
                        )}
                      </td>
                      <td className="num">{fmtMoney0(h.valueTry)}<div className="muted">{fmtMoney0(h.valueUsd, 'USD')}</div></td>
                      <td className="num">
                        <div className={pnlClass(h.unrealizedTry)}>{fmtSigned(h.unrealizedTry, 'TRY', 0)} <small>{fmtPct(h.unrealizedPctTry)}</small></div>
                        <div className={`small ${pnlClass(h.unrealizedUsd)}`}>{fmtSigned(h.unrealizedUsd, 'USD', 0)} <small>{fmtPct(h.unrealizedPctUsd)}</small></div>
                      </td>
                      <td className="num">%{fmtNum(h.weight, 1)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="muted">Kâr/zarar her işlemin kendi günündeki kurla hesaplanır; dolar satırı "dolar bazında" kazancını gösterir.</p>
          </section>
        </div>
      )}

      {showForm && <AssetForm onCancel={() => setShowForm(false)} onSave={(a) => run(() => api.createAsset(a)).then(() => setShowForm(false))} />}

      {closed.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2>Kapanmış pozisyonlar</h2>
          {closed.map((h) => (
            <div key={h.assetId} className="item">
              <span className="title">{ASSET_ICON[h.type]} {h.name}</span>
              <span className={pnlClass(h.realizedTry)}>{fmtSigned(h.realizedTry, 'TRY', 0)}</span>
              <span className={`muted ${pnlClass(h.realizedUsd)}`}>{fmtSigned(h.realizedUsd, 'USD', 0)}</span>
            </div>
          ))}
        </section>
      )}

      {unused.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2>İşlemi olmayan varlıklar</h2>
          {unused.map((a) => (
            <div key={a.id} className="item">
              <span className="title">{ASSET_ICON[a.type]} {a.name} <span className="muted">{a.symbol}</span></span>
              <button className="btn ghost del" onClick={() => confirm(`"${a.name}" silinsin mi?`) && run(() => api.deleteAsset(a.id))}>✕</button>
            </div>
          ))}
        </section>
      )}
    </>
  )
}

function RateEditor({ currency, rate, fallback, onSave }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  const start = () => { setValue(rate ? String(rate.rateTry).replace('.', ',') : ''); setEditing(true) }
  const save = (e) => {
    e.preventDefault()
    const n = parseNum(value)
    if (!n || n <= 0) return
    onSave(n)
    setEditing(false)
  }
  if (editing) {
    return (
      <form className="row rate-row" onSubmit={save}>
        <span>{currency}/TRY</span>
        <input autoFocus className="num-input" value={value} onChange={(e) => setValue(e.target.value)} onBlur={() => setEditing(false)} onKeyDown={(e) => e.key === 'Escape' && setEditing(false)} />
      </form>
    )
  }
  return (
    <div className="row rate-row" onClick={start} title="Düzenlemek için tıkla">
      <span className="muted">{currency}/TRY</span>
      <div className="spacer" />
      {rate ? <strong title={`TCMB döviz alış · ${timeAgo(rate.updatedAt)}`}>{fmtNum(rate.rateTry)}</strong>
        : fallback ? <span className="tag overdue" title="Kur girilmemiş; son işlemdeki kur kullanılıyor">{fmtNum(fallback)} ?</span>
          : <span className="tag overdue">gir</span>}
      <span className="edit-hint">✎</span>
    </div>
  )
}

function PriceCell({ holding: h, onSave }) {
  const [editing, setEditing] = useState(false)
  const [value, setValue] = useState('')
  // Otomatik kaynaklı fiyatlar elle değiştirilmez; bir sonraki güncellemede üzerine yazılırdı
  if (h.priceSource && h.priceSource !== 'MANUAL') {
    return <span>{fmtMoney(h.price, h.priceCurrency)}</span>
  }
  const save = (e) => {
    e.preventDefault()
    const n = parseNum(value)
    if (!n || n <= 0) return
    onSave(n)
    setEditing(false)
  }
  if (editing) {
    return (
      <form onSubmit={save}>
        <input autoFocus className="num-input" value={value} onChange={(e) => setValue(e.target.value)}
          onBlur={() => setEditing(false)} onKeyDown={(e) => e.key === 'Escape' && setEditing(false)} placeholder={currencySymbol(h.priceCurrency)} />
      </form>
    )
  }
  return (
    <button className="price-btn" onClick={() => { setValue(''); setEditing(true) }} title="Güncel fiyatı gir">
      {fmtMoney(h.price, h.priceCurrency)} <span className="edit-hint">✎</span>
      {h.priceIsFallback && <div className="tag overdue" title="Güncel fiyat girilmemiş; son işlem fiyatı kullanılıyor">son işlem fiyatı</div>}
    </button>
  )
}

function SourceTag({ holding: h, onClick }) {
  const src = h.priceSource ?? 'MANUAL'
  const stale = src !== 'MANUAL' && isStale(h.priceUpdatedAt)
  const cls = h.priceError ? 'tag sell' : stale ? 'tag overdue' : 'tag'
  const title = h.priceError ? `Son güncelleme başarısız: ${h.priceError}` : 'Fiyat kaynağını değiştirmek için tıkla'
  return (
    <div>
      <button className={`source-tag ${cls}`} onClick={onClick} title={title}>
        {h.priceError ? '⚠ ' : ''}{PRICE_SOURCE[src]}{src !== 'MANUAL' && h.priceUpdatedAt ? ` · ${timeAgo(h.priceUpdatedAt)}` : ''}
      </button>
    </div>
  )
}

function SourceEditor({ asset, onSave, onCancel }) {
  const [source, setSource] = useState(asset?.priceSource ?? 'MANUAL')
  const [symbol, setSymbol] = useState(asset?.sourceSymbol ?? '')
  const needsSymbol = source === 'TRUNCGIL' || source === 'YAHOO'
  const hint = source === 'TRUNCGIL' ? 'gram-altin, ceyrek-altin, gumus, ons…' : source === 'YAHOO' ? 'THYAO.IS, AAPL…' : ''
  return (
    <form className="source-editor" onSubmit={(e) => { e.preventDefault(); onSave(source, needsSymbol ? symbol.trim() : null) }}>
      <select value={source} onChange={(e) => setSource(e.target.value)}>
        <option value="MANUAL">Elle</option>
        <option value="TRUNCGIL">Truncgil (altın)</option>
        <option value="YAHOO">Yahoo (hisse)</option>
        {asset?.type === 'CURRENCY' && <option value="RATE">TCMB kuru</option>}
      </select>
      {needsSymbol && <input value={symbol} onChange={(e) => setSymbol(e.target.value)} placeholder={hint} />}
      <div className="row">
        <button className="btn primary">Kaydet</button>
        <button type="button" className="btn" onClick={onCancel}>Vazgeç</button>
      </div>
    </form>
  )
}

function PriceStatusBar({ status, refreshing, onRefresh }) {
  const failed = status ? Object.entries(status.sources).filter(([, v]) => v !== 'ok') : []
  return (
    <div className="price-status">
      <span className="muted">
        {status
          ? <>Fiyatlar otomatik güncelleniyor · son: {timeAgo(status.finishedAt)} ·{' '}
            {Object.entries(status.sources).map(([k, v]) => (
              <span key={k} className={v === 'ok' ? 'pos' : 'neg'} title={v === 'ok' ? 'Başarılı' : v}> {k} {v === 'ok' ? '✓' : '✗'}</span>
            ))}</>
          : 'Fiyatlar henüz güncellenmedi'}
      </span>
      <button className="btn" onClick={onRefresh} disabled={refreshing}>{refreshing ? 'Güncelleniyor…' : '↻ Güncelle'}</button>
      {failed.length > 0 && <div className="warn small" style={{ width: '100%' }}>{failed.map(([k, v]) => `${k}: ${v}`).join(' · ')}</div>}
    </div>
  )
}

export function AssetForm({ onSave, onCancel }) {
  const [form, setForm] = useState({ ...ASSET_PRESETS[0] })
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })
  const submit = (e) => {
    e.preventDefault()
    if (!form.symbol.trim() || !form.name.trim()) return
    onSave({ symbol: form.symbol.trim(), name: form.name.trim(), type: form.type, priceCurrency: form.priceCurrency, unit: form.unit || null })
  }
  return (
    <form className="card form" onSubmit={submit} style={{ marginTop: 16 }}>
      <h2>Yeni varlık</h2>
      <div className="row presets">
        {ASSET_PRESETS.map((p) => (
          <button type="button" key={p.label ?? p.symbol} className={`btn ${form === p || (form.symbol === p.symbol && p.symbol) ? 'on' : ''}`}
            onClick={() => setForm({ ...p })}>{ASSET_ICON[p.type]} {p.label ?? p.name}</button>
        ))}
      </div>
      <div className="row">
        <input placeholder="Sembol (ör. THYAO)" value={form.symbol} onChange={set('symbol')} style={{ width: 160 }} />
        <input placeholder="Ad (ör. Türk Hava Yolları)" value={form.name} onChange={set('name')} style={{ flex: 1 }} />
      </div>
      <div className="row">
        <select value={form.type} onChange={set('type')}>
          {Object.entries(ASSET_TYPE).map(([k, v]) => <option key={k} value={k}>{ASSET_ICON[k]} {v}</option>)}
        </select>
        <label className="muted row">Fiyat birimi
          <select value={form.priceCurrency} onChange={set('priceCurrency')}>
            <option value="TRY">₺ TRY</option><option value="USD">$ USD</option>
          </select>
        </label>
        <input placeholder="Birim (gr, lot, adet)" value={form.unit ?? ''} onChange={set('unit')} style={{ width: 160 }} />
      </div>
      <p className="muted" style={{ margin: 0 }}>Fiyat kaynağı otomatik seçilir: altın → Truncgil, hisse → Yahoo (BIST için ".IS"), USD/EUR → TCMB, fon → elle. Sonradan tablodaki kaynak etiketine tıklayıp değiştirebilirsin.</p>
      <div className="row">
        <button className="btn primary">Kaydet</button>
        <button type="button" className="btn" onClick={onCancel}>Vazgeç</button>
      </div>
    </form>
  )
}
