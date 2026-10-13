// Tarihleri yerel saat diliminde YYYY-MM-DD olarak işle (toISOString UTC'ye kaydırır).
export const toISO = (d) =>
  `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`

export const today = () => toISO(new Date())

export const addDays = (iso, n) => {
  const [y, m, d] = iso.split('-').map(Number)
  return toISO(new Date(y, m - 1, d + n))
}

export const formatDate = (iso, opts = { weekday: 'long', day: 'numeric', month: 'long' }) => {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y, m - 1, d).toLocaleDateString('tr-TR', opts)
}

export const PRIORITY = { HIGH: 'Yüksek', MEDIUM: 'Orta', LOW: 'Düşük' }

export const CATEGORY = {
  HEALTH: 'Sağlık', CAREER: 'Kariyer', FINANCE: 'Finans', EDUCATION: 'Eğitim',
  PERSONAL: 'Kişisel', RELATIONSHIPS: 'İlişkiler', TRAVEL: 'Seyahat', OTHER: 'Diğer',
}

export const STATUS = { ACTIVE: 'Aktif', COMPLETED: 'Tamamlandı', PAUSED: 'Beklemede', ABANDONED: 'Bırakıldı' }

export const DAYS = [
  ['MONDAY', 'Pzt'], ['TUESDAY', 'Sal'], ['WEDNESDAY', 'Çar'], ['THURSDAY', 'Per'],
  ['FRIDAY', 'Cum'], ['SATURDAY', 'Cmt'], ['SUNDAY', 'Paz'],
]

export const MOODS = ['😞', '😕', '😐', '🙂', '😄']

/** Verilen tarihin haftasının pazartesisi. */
export const startOfWeek = (iso) => {
  const [y, m, d] = iso.split('-').map(Number)
  const day = new Date(y, m - 1, d).getDay() // 0 = Pazar
  return addDays(iso, day === 0 ? -6 : 1 - day)
}

export const daysBetween = (a, b) => {
  const [y1, m1, d1] = a.split('-').map(Number)
  const [y2, m2, d2] = b.split('-').map(Number)
  return Math.round((Date.UTC(y2, m2 - 1, d2) - Date.UTC(y1, m1 - 1, d1)) / 86400000)
}

export const TASK_STATUS = { TODO: 'Yapılacak', IN_PROGRESS: 'Yapılıyor', DONE: 'Bitti' }

export const CATEGORY_ICON = {
  HEALTH: '💪', CAREER: '💼', FINANCE: '💰', EDUCATION: '🎓',
  PERSONAL: '🌱', RELATIONSHIPS: '❤️', TRAVEL: '✈️', OTHER: '⭐',
}
