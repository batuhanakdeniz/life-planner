// Para ve sayı biçimlendirme (Türkçe: 1.234,56 ₺)

const SYMBOL = { TRY: '₺', USD: '$', EUR: '€' }

export const fmtMoney = (v, currency = 'TRY', digits = 2) =>
  v == null ? '—' : new Intl.NumberFormat('tr-TR', { style: 'currency', currency, minimumFractionDigits: digits, maximumFractionDigits: digits }).format(v)

/** Büyük tutarlar için kuruşsuz. */
export const fmtMoney0 = (v, currency = 'TRY') => fmtMoney(v, currency, 0)

export const fmtNum = (v, max = 4) =>
  v == null ? '—' : new Intl.NumberFormat('tr-TR', { maximumFractionDigits: max }).format(v)

/** +%5,54 / -%1,20 */
export const fmtPct = (v) =>
  v == null ? '' : `${v > 0 ? '+' : v < 0 ? '−' : ''}%${new Intl.NumberFormat('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }).format(Math.abs(v))}`

/** +1.234 ₺ biçiminde, işaretli */
export const fmtSigned = (v, currency = 'TRY', digits = 2) =>
  v == null ? '—' : `${v > 0 ? '+' : v < 0 ? '−' : ''}${fmtMoney(Math.abs(v), currency, digits)}`

export const pnlClass = (v) => (v > 0 ? 'pos' : v < 0 ? 'neg' : '')

export const currencySymbol = (c) => SYMBOL[c] ?? c

/**
 * Türkçe sayı girişini çözer: virgül ondalık, nokta binlik ayırıcıdır.
 *   "5.400,50" → 5400.5   "6.250" → 6250   "1.250.000" → 1250000
 *   "43,5" → 43.5         "43.5" → 43.5 (binlik kalıbına uymayan tek nokta ondalık sayılır)
 */
export const parseNum = (s) => {
  if (s == null || String(s).trim() === '') return null
  let t = String(s).trim().replace(/\s/g, '')
  if (t.includes(',')) {
    if ((t.match(/,/g) || []).length > 1) return NaN
    t = t.replace(/\./g, '').replace(',', '.')
  } else if (/^\d{1,3}(\.\d{3})+$/.test(t)) {
    t = t.replace(/\./g, '')
  }
  if (!/^-?\d+(\.\d+)?$/.test(t)) return NaN
  return Number(t)
}

export const ASSET_TYPE = { GOLD: 'Altın', CURRENCY: 'Döviz', STOCK: 'Hisse', FUND: 'Fon' }
export const ASSET_ICON = { GOLD: '🥇', CURRENCY: '💵', STOCK: '📈', FUND: '🧺' }
export const ASSET_COLOR = { GOLD: '#fbbf24', CURRENCY: '#22d3ee', STOCK: '#a78bfa', FUND: '#4ade80' }
export const FREQUENCY = { WEEKLY: 'Haftalık', MONTHLY: 'Aylık', YEARLY: 'Yıllık' }

/** Yeni varlık eklerken hızlı seçimler */
export const ASSET_PRESETS = [
  { symbol: 'GRAM_ALTIN', name: 'Gram altın', type: 'GOLD', priceCurrency: 'TRY', unit: 'gr' },
  { symbol: 'CEYREK_ALTIN', name: 'Çeyrek altın', type: 'GOLD', priceCurrency: 'TRY', unit: 'adet' },
  { symbol: 'ONS_ALTIN', name: 'Ons altın', type: 'GOLD', priceCurrency: 'USD', unit: 'ons' },
  { symbol: 'GUMUS', name: 'Gram gümüş', type: 'GOLD', priceCurrency: 'TRY', unit: 'gr' },
  { symbol: 'USD', name: 'Amerikan doları', type: 'CURRENCY', priceCurrency: 'TRY', unit: '$' },
  { symbol: 'EUR', name: 'Euro', type: 'CURRENCY', priceCurrency: 'TRY', unit: '€' },
  { symbol: '', name: '', type: 'STOCK', priceCurrency: 'TRY', unit: 'lot', label: 'BIST hissesi' },
  { symbol: '', name: '', type: 'STOCK', priceCurrency: 'USD', unit: 'adet', label: 'ABD hissesi' },
  { symbol: '', name: '', type: 'FUND', priceCurrency: 'TRY', unit: 'pay', label: 'Yatırım fonu' },
]
