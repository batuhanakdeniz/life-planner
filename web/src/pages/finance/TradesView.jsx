import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../../api.js'
import { formatDate, today } from '../../util.js'
import { ASSET_ICON, currencySymbol, fmtMoney, fmtNum, parseNum } from '../../money.js'

const emptyForm = () => ({ side: 'BUY', date: today(), quantity: '', unitPrice: '', usdTryRate: '', fee: '', note: '' })

export default function TradesView() {
  const [assets, setAssets] = useState([])
  const [trades, setTrades] = useState([])
  const [usdRate, setUsdRate] = useState(null)
  const [filter, setFilter] = useState('')
  const [form, setForm] = useState(emptyForm())
  const [assetId, setAssetId] = useState('')
  const [editing, setEditing] = useState(null)
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    Promise.all([api.assets(), api.trades(filter || null), api.rates()])
      .then(([a, t, r]) => {
        setAssets(a)
        setTrades(t)
        setUsdRate(r.find((x) => x.currency === 'USD')?.rateTry ?? null)
        if (!assetId && a.length) setAssetId(String(a[0].id))
      })
      .catch((e) => setError(e.message))
  }, [filter]) // eslint-disable-line react-hooks/exhaustive-deps
  useEffect(load, [load])

  const asset = assets.find((a) => String(a.id) === String(assetId))

  // Tarih seçilince o günün TCMB kuru otomatik dolar (kullanıcı kendisi yazdıysa dokunulmaz)
  const [tcmbInfo, setTcmbInfo] = useState(null)
  const autoRate = useRef(null)
  useEffect(() => {
    if (!form.date) return
    let cancelled = false
    api.historicalRate(form.date).then((r) => {
      if (cancelled) return
      setTcmbInfo(r)
      if (!r) return
      const value = String(r.rateTry).replace('.', ',')
      // Güncelleyici gecikmeli çalışır; karşılaştırma için önceki otomatik değeri şimdiden yakala
      const previousAuto = autoRate.current
      autoRate.current = value
      setForm((f) => (f.usdTryRate === '' || f.usdTryRate === previousAuto ? { ...f, usdTryRate: value } : f))
    }).catch(() => !cancelled && setTcmbInfo(null))
    return () => { cancelled = true }
  }, [form.date])
  const set = (k) => (e) => setForm({ ...form, [k]: e.target.value })

  // Döviz alımında (USD varlığı) birim fiyat zaten USD/TRY kurudur
  const isUsdAsset = asset?.type === 'CURRENCY' && asset?.symbol === 'USD'
  const rateValue = isUsdAsset ? form.unitPrice : form.usdTryRate

  const preview = useMemo(() => {
    const q = parseNum(form.quantity), p = parseNum(form.unitPrice), r = parseNum(rateValue) ?? usdRate, f = parseNum(form.fee) ?? 0
    if (!q || !p || !r || !asset) return null
    const gross = q * p
    const total = form.side === 'BUY' ? gross + f : gross - f
    return asset.priceCurrency === 'USD' ? { tr: total * r, usd: total } : { tr: total, usd: total / r }
  }, [form, rateValue, asset, usdRate])

  const submit = (e) => {
    e.preventDefault()
    const body = {
      assetId: Number(assetId), side: form.side, date: form.date,
      quantity: parseNum(form.quantity), unitPrice: parseNum(form.unitPrice), usdTryRate: parseNum(rateValue),
      fee: parseNum(form.fee), note: form.note || null,
    }
    if (Object.values(body).some((v) => Number.isNaN(v))) { setError('Sayı alanlarından biri geçersiz'); return }
    if (!body.quantity || !body.unitPrice) { setError('Miktar ve birim fiyat gerekli'); return }
    const call = editing ? api.updateTrade(editing, body) : api.createTrade(body)
    call.then(() => { autoRate.current = null; setForm(emptyForm()); setEditing(null); setError(null); load() }).catch((err) => setError(err.message))
  }

  const edit = (t) => {
    autoRate.current = null
    setEditing(t.id)
    setAssetId(String(t.assetId))
    const s = (v) => (v == null ? '' : String(v).replace('.', ','))
    setForm({ side: t.side, date: t.date, quantity: s(t.quantity), unitPrice: s(t.unitPrice), usdTryRate: t.rateEstimated ? '' : s(t.usdTryRate), fee: s(t.fee), note: t.note ?? '' })
    window.scrollTo({ top: 0, behavior: 'smooth' })
  }

  if (assets.length === 0) {
    return <section className="card empty-state"><h2>Önce bir varlık ekle</h2><p className="muted">"Portföy" sekmesinden ör. Gram altın, USD veya bir hisse ekleyebilirsin.</p></section>
  }

  return (
    <>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}
      <form className="card form trade-form" onSubmit={submit}>
        <div className="row">
          <h2 style={{ margin: 0 }}>{editing ? 'İşlemi düzenle' : 'Yeni işlem'}</h2>
          <div className="spacer" />
          <div className="tabs side-tabs">
            <button type="button" className={form.side === 'BUY' ? 'on buy' : ''} onClick={() => setForm({ ...form, side: 'BUY' })}>Alış</button>
            <button type="button" className={form.side === 'SELL' ? 'on sell' : ''} onClick={() => setForm({ ...form, side: 'SELL' })}>Satış</button>
          </div>
        </div>
        <div className="trade-grid">
          <label>Varlık
            <select value={assetId} onChange={(e) => setAssetId(e.target.value)}>
              {assets.map((a) => <option key={a.id} value={a.id}>{ASSET_ICON[a.type]} {a.name}</option>)}
            </select>
          </label>
          <label>Tarih<input type="date" value={form.date} max={today()} onChange={set('date')} /></label>
          <label>Miktar {asset?.unit && <span className="muted">({asset.unit})</span>}
            <input inputMode="decimal" placeholder="10" value={form.quantity} onChange={set('quantity')} /></label>
          <label>Birim fiyat <span className="muted">({currencySymbol(asset?.priceCurrency)})</span>
            <input inputMode="decimal" placeholder={isUsdAsset ? 'Alış kuru' : '0,00'} value={form.unitPrice} onChange={set('unitPrice')} /></label>
          <label>
            <span className="row" style={{ gap: 6 }}>O günkü USD/TRY
              {!isUsdAsset && tcmbInfo && <button type="button" className="link-btn" title="TCMB döviz alış kuru"
                onClick={() => setForm({ ...form, usdTryRate: String(tcmbInfo.rateTry).replace('.', ',') })}>
                TCMB {formatDate(tcmbInfo.rateDate, { day: 'numeric', month: 'short' })}: {fmtNum(tcmbInfo.rateTry)}</button>}
            </span>
            {isUsdAsset
              ? <input disabled value={form.unitPrice} title="Dolar alımında birim fiyat kurun kendisidir" />
              : <input inputMode="decimal" placeholder="Bilmiyorsan boş bırak" value={form.usdTryRate} onChange={set('usdTryRate')}
                title="Boş bırakılırsa güncel kur kullanılır ve işlem ≈ tahmini olarak işaretlenir" />}
          </label>
          <label>Masraf <span className="muted">({currencySymbol(asset?.priceCurrency)}, isteğe bağlı)</span>
            <input inputMode="decimal" placeholder="0" value={form.fee} onChange={set('fee')} /></label>
        </div>
        <input placeholder="Not (isteğe bağlı)" value={form.note} onChange={set('note')} />
        <p className="muted" style={{ margin: 0 }}>Ondalık için virgül kullan (6.250,50). Nokta binlik ayırıcıdır. Kur, seçtiğin tarihin TCMB kuruyla otomatik dolar; istersen değiştirebilirsin.</p>
        <div className="row">
          <button className={`btn primary ${form.side === 'SELL' ? 'sell-btn' : ''}`}>{editing ? 'Güncelle' : form.side === 'BUY' ? 'Alışı kaydet' : 'Satışı kaydet'}</button>
          {editing && <button type="button" className="btn" onClick={() => { setEditing(null); setForm(emptyForm()) }}>Vazgeç</button>}
          <div className="spacer" />
          {preview && <span className="preview">Toplam: <strong>{fmtMoney(preview.tr)}</strong> · <strong>{fmtMoney(preview.usd, 'USD')}</strong></span>}
        </div>
      </form>

      <section className="card" style={{ marginTop: 16 }}>
        <div className="row" style={{ marginBottom: 8 }}>
          <h2 style={{ margin: 0 }}>İşlem geçmişi</h2>
          <div className="spacer" />
          <select value={filter} onChange={(e) => setFilter(e.target.value)}>
            <option value="">Tüm varlıklar</option>
            {assets.map((a) => <option key={a.id} value={a.id}>{a.name}</option>)}
          </select>
        </div>
        <div className="table-wrap">
          <table className="fin-table">
            <thead><tr><th>Tarih</th><th>İşlem</th><th>Varlık</th><th className="num">Miktar</th><th className="num">Birim fiyat</th><th className="num">USD/TRY</th><th className="num">Toplam</th><th /></tr></thead>
            <tbody>
              {trades.length === 0 && <tr><td colSpan={8} className="empty">Henüz işlem yok</td></tr>}
              {trades.map((t) => (
                <tr key={t.id} className={editing === t.id ? 'selected' : ''}>
                  <td>{formatDate(t.date, { day: 'numeric', month: 'short', year: 'numeric' })}</td>
                  <td><span className={`tag ${t.side === 'BUY' ? 'buy' : 'sell'}`}>{t.side === 'BUY' ? 'Alış' : 'Satış'}</span></td>
                  <td>{t.assetName}{t.note && <div className="muted">{t.note}</div>}</td>
                  <td className="num">{fmtNum(t.quantity)} <span className="muted">{t.unit}</span></td>
                  <td className="num">{fmtMoney(t.unitPrice, t.priceCurrency)}</td>
                  <td className="num muted" title={t.rateEstimated ? 'Kur bilinmiyordu; kayıt anındaki güncel kur kullanıldı' : undefined}>
                    {t.rateEstimated && <span className="warn">≈ </span>}{fmtNum(t.usdTryRate)}</td>
                  <td className="num">{fmtMoney(t.totalTry)}<div className="muted">{fmtMoney(t.totalUsd, 'USD')}</div></td>
                  <td className="num nowrap">
                    <button className="btn ghost" title="Düzenle" onClick={() => edit(t)}>✎</button>
                    <button className="btn ghost del" title="Sil" onClick={() => confirm('Bu işlem silinsin mi?') && api.deleteTrade(t.id).then(load).catch((e) => setError(e.message))}>✕</button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </section>
    </>
  )
}
