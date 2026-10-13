// Backend REST API istemcisi. Vite proxy sayesinde göreli /api yolları kullanılır.

async function request(method, path, body) {
  const res = await fetch(`/api${path}`, {
    method,
    headers: body !== undefined ? { 'Content-Type': 'application/json' } : undefined,
    body: body !== undefined ? JSON.stringify(body) : undefined,
  })
  if (res.status === 204) return null
  // Günlük kaydı olmayan gün için 404 normaldir
  if (res.status === 404 && (path.startsWith('/journal/') || path.startsWith('/finance/rates/history') || path.startsWith('/bulletins?') || path === '/bulletins')) return null
  if (!res.ok) {
    const problem = await res.json().catch(() => ({}))
    throw new Error(problem.detail || `İstek başarısız: ${res.status}`)
  }
  return res.json()
}

const get = (p) => request('GET', p)
const post = (p, b) => request('POST', p, b)
const put = (p, b) => request('PUT', p, b)
const del = (p) => request('DELETE', p)

export const api = {
  dashboard: (date) => get(`/dashboard?date=${date}`),

  tasksForDay: (date) => get(`/tasks?date=${date}`),
  tasksInRange: (from, to) => get(`/tasks?from=${from}&to=${to}`),
  somedayTasks: () => get('/tasks'),
  createTask: (t) => post('/tasks', t),
  updateTask: (id, t) => put(`/tasks/${id}`, t),
  setTaskDone: (id, done) => (done ? post : del)(`/tasks/${id}/complete`),
  deleteTask: (id) => del(`/tasks/${id}`),
  taskBoard: () => get('/tasks/board'),
  setTaskStatus: (id, status) => request('PATCH', `/tasks/${id}/status`, { status }),

  habits: (date, includeArchived = false) => get(`/habits?date=${date}&includeArchived=${includeArchived}`),
  createHabit: (h) => post('/habits', h),
  updateHabit: (id, h) => put(`/habits/${id}`, h),
  deleteHabit: (id) => del(`/habits/${id}`),
  habitHistory: (id, from, to) => get(`/habits/${id}/history?from=${from}&to=${to}`),
  setHabitDone: (id, date, done) => (done ? post : del)(`/habits/${id}/check?date=${date}`),

  goals: (status) => get(`/goals${status ? `?status=${status}` : ''}`),
  createGoal: (g) => post('/goals', g),
  updateGoal: (id, g) => put(`/goals/${id}`, g),
  deleteGoal: (id) => del(`/goals/${id}`),
  addMilestone: (goalId, title) => post(`/goals/${goalId}/milestones`, { title }),
  setMilestoneDone: (id, done) => (done ? post : del)(`/goals/milestones/${id}/done`),
  deleteMilestone: (id) => del(`/goals/milestones/${id}`),

  // --- Finans ---
  portfolio: () => get('/finance/portfolio'),
  assets: () => get('/finance/assets'),
  createAsset: (a) => post('/finance/assets', a),
  updateAsset: (id, a) => put(`/finance/assets/${id}`, a),
  setAssetPrice: (id, price) => put(`/finance/assets/${id}/price`, { price }),
  deleteAsset: (id) => del(`/finance/assets/${id}`),
  setAssetSource: (id, priceSource, sourceSymbol) => put(`/finance/assets/${id}/source`, { priceSource, sourceSymbol }),
  priceStatus: () => get('/finance/prices/status'),
  refreshPrices: () => post('/finance/prices/refresh'),
  historicalRate: (date) => get(`/finance/rates/history?date=${date}`),
  trades: (assetId) => get(`/finance/trades${assetId ? `?assetId=${assetId}` : ''}`),
  createTrade: (t) => post('/finance/trades', t),
  updateTrade: (id, t) => put(`/finance/trades/${id}`, t),
  deleteTrade: (id) => del(`/finance/trades/${id}`),
  rates: () => get('/finance/rates'),
  setRate: (currency, rateTry) => put(`/finance/rates/${currency}`, { rateTry }),
  payments: () => get('/finance/payments'),
  createPayment: (p) => post('/finance/payments', p),
  updatePayment: (id, p) => put(`/finance/payments/${id}`, p),
  deletePayment: (id) => del(`/finance/payments/${id}`),
  payPayment: (id, body) => post(`/finance/payments/${id}/pay`, body ?? {}),
  undoPayment: (logId) => del(`/finance/payments/logs/${logId}`),

  // --- Bülten ---
  bulletin: (date) => get(`/bulletins${date ? `?date=${date}` : ''}`),
  bulletinDates: () => get('/bulletins/dates'),
  bulletinChannels: () => get('/bulletins/channels'),

  journal: (from, to) => get(`/journal?from=${from}&to=${to}`),
  journalEntry: (date) => get(`/journal/${date}`),
  saveJournal: (date, entry) => put(`/journal/${date}`, entry),
}
