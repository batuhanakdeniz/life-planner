import { useCallback, useEffect, useState } from 'react'
import { api } from '../api.js'
import { addDays, CATEGORY, CATEGORY_ICON, daysBetween, formatDate, STATUS, today } from '../util.js'

export default function Goals() {
  const [goals, setGoals] = useState([])
  const [filter, setFilter] = useState('ACTIVE')
  const [selected, setSelected] = useState(null)
  const [editing, setEditing] = useState(null) // null | 'new' | goal
  const [error, setError] = useState(null)

  const load = useCallback(() => {
    api.goals(filter === 'ALL' ? null : filter).then(setGoals).catch((e) => setError(e.message))
  }, [filter])
  useEffect(load, [load])

  const run = (fn) => fn().then(load).catch((e) => setError(e.message))
  const dated = goals.filter((g) => g.targetDate).sort((a, b) => a.targetDate.localeCompare(b.targetDate))
  const undated = goals.filter((g) => !g.targetDate)
  const selectedGoal = goals.find((g) => g.id === selected)

  return (
    <>
      <div className="row">
        <h1>🎯 Hedefler</h1>
        <div className="spacer" />
        <div className="tabs">
          {[['ACTIVE', 'Aktif'], ['COMPLETED', 'Tamamlanan'], ['ALL', 'Tümü']].map(([k, l]) => (
            <button key={k} className={filter === k ? 'on' : ''} onClick={() => setFilter(k)}>{l}</button>
          ))}
        </div>
        <button className="btn primary" onClick={() => setEditing('new')}>+ Yeni hedef</button>
      </div>
      <p className="subtitle">Hedeflerin, hedef tarihlerine göre zaman çizelgesinde.</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      {editing && (
        <GoalForm key={editing === 'new' ? 'new' : editing.id} goal={editing === 'new' ? null : editing} onCancel={() => setEditing(null)}
          onSave={(data) => run(() => (editing === 'new' ? api.createGoal(data) : api.updateGoal(editing.id, data))).then(() => setEditing(null))} />
      )}

      <section className="card">
        {dated.length === 0
          ? <p className="empty">Tarihli hedef yok. Hedefe bir tarih verirsen burada görünür.</p>
          : <Timeline goals={dated} selected={selected} onSelect={(id) => setSelected(selected === id ? null : id)} />}
      </section>

      {undated.length > 0 && (
        <section className="card" style={{ marginTop: 16 }}>
          <h2>Tarihsiz hedefler</h2>
          {undated.map((g) => (
            <div key={g.id} className={`undated ${selected === g.id ? 'selected' : ''}`} onClick={() => setSelected(selected === g.id ? null : g.id)}>
              <span>{CATEGORY_ICON[g.category]} {g.title}</span>
              <div className="progress" style={{ flex: 1, maxWidth: 200 }}><div style={{ width: `${g.progress}%` }} /></div>
              <span className="muted">%{g.progress}</span>
            </div>
          ))}
        </section>
      )}

      {selectedGoal && (
        <GoalDetail goal={selectedGoal} run={run} onEdit={() => setEditing(selectedGoal)}
          onDelete={() => confirm(`"${selectedGoal.title}" silinsin mi? Bağlı görevler silinmez.`) && run(() => api.deleteGoal(selectedGoal.id)).then(() => setSelected(null))} />
      )}
    </>
  )
}

function Timeline({ goals, selected, onSelect }) {
  const now = today()
  const min = [now, ...goals.map((g) => g.startDate)].sort()[0]
  const lastTarget = goals[goals.length - 1].targetDate
  const max = addDays(lastTarget > now ? lastTarget : now, 30)
  const span = Math.max(daysBetween(min, max), 1)
  const pos = (iso) => `${(daysBetween(min, iso) / span) * 100}%`

  // Ay çizgileri; uzun aralıklarda kalabalık olmasın diye seyreltilir
  const months = []
  const [y, m] = min.split('-').map(Number)
  for (let i = 1; ; i++) {
    const d = new Date(y, m - 1 + i, 1)
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-01`
    if (iso > max) break
    months.push(iso)
  }
  const step = months.length > 24 ? 6 : months.length > 12 ? 3 : 1

  return (
    <div className="timeline">
      <div className="tl-row tl-axis">
        <div className="tl-label" />
        <div className="tl-track">
          {months.filter((_, i) => i % step === 0).map((iso, i, shown) => {
            // İlk etikette ve yıl değiştiğinde yılı da yaz
            const newYear = i === 0 || iso.slice(0, 4) !== shown[i - 1].slice(0, 4)
            return (
              <span key={iso} className="tl-month" style={{ left: pos(iso) }}>
                {formatDate(iso, newYear ? { month: 'short', year: 'numeric' } : { month: 'short' })}
              </span>
            )
          })}
        </div>
      </div>
      {goals.map((g) => {
        const left = daysBetween(min, g.startDate) / span * 100
        const width = Math.max(daysBetween(g.startDate, g.targetDate) / span * 100, 1)
        const daysLeft = daysBetween(now, g.targetDate)
        const late = daysLeft < 0 && g.status === 'ACTIVE'
        return (
          <div key={g.id} className={`tl-row goal ${selected === g.id ? 'selected' : ''} ${g.status}`} onClick={() => onSelect(g.id)}>
            <div className="tl-label">
              <span>{CATEGORY_ICON[g.category]}</span>
              <span className="tl-title">{g.title}</span>
            </div>
            <div className="tl-track">
              {months.map((iso) => <span key={iso} className="tl-grid" style={{ left: pos(iso) }} />)}
              <span className="tl-now" style={{ left: pos(now) }} />
              <div className={`tl-bar ${late ? 'late' : ''}`} style={{ left: `${left}%`, width: `${width}%` }}>
                <div className="tl-fill" style={{ width: `${g.progress}%` }} />
              </div>
              <span className="tl-end" style={{ left: `calc(${left + width}% + 8px)` }}>
                %{g.progress} · {g.status === 'COMPLETED' ? '✓ Tamamlandı' : late ? `${-daysLeft} gün gecikti` : `${daysLeft} gün kaldı`}
              </span>
            </div>
          </div>
        )
      })}
      <div className="tl-row tl-today-row">
        <div className="tl-label" />
        <div className="tl-track"><span className="tl-today" style={{ left: pos(now) }}><b>Bugün</b></span></div>
      </div>
    </div>
  )
}

function GoalForm({ goal, onSave, onCancel }) {
  const [title, setTitle] = useState(goal?.title ?? '')
  const [category, setCategory] = useState(goal?.category ?? 'PERSONAL')
  const [targetDate, setTargetDate] = useState(goal?.targetDate ?? '')
  const [status, setStatus] = useState(goal?.status ?? 'ACTIVE')
  const [description, setDescription] = useState(goal?.description ?? '')
  const [milestones, setMilestones] = useState('')

  const submit = (e) => {
    e.preventDefault()
    if (!title.trim()) return
    onSave({
      title: title.trim(), category, status, description: description || null, targetDate: targetDate || null,
      milestones: goal ? null : milestones.split('\n').map((s) => s.trim()).filter(Boolean),
    })
  }

  return (
    <form className="card form" onSubmit={submit} style={{ marginBottom: 20 }}>
      <h2>{goal ? 'Hedefi düzenle' : 'Yeni hedef'}</h2>
      <input placeholder="Ör. İngilizce B2 seviyesine ulaş" value={title} onChange={(e) => setTitle(e.target.value)} autoFocus />
      <div className="row">
        <select value={category} onChange={(e) => setCategory(e.target.value)}>
          {Object.entries(CATEGORY).map(([k, v]) => <option key={k} value={k}>{CATEGORY_ICON[k]} {v}</option>)}
        </select>
        <label className="muted row">Hedef tarih <input type="date" value={targetDate} onChange={(e) => setTargetDate(e.target.value)} /></label>
        {goal && (
          <select value={status} onChange={(e) => setStatus(e.target.value)}>
            {Object.entries(STATUS).map(([k, v]) => <option key={k} value={k}>{v}</option>)}
          </select>
        )}
      </div>
      <textarea style={{ minHeight: 70 }} placeholder="Neden bu hedef? (isteğe bağlı)" value={description} onChange={(e) => setDescription(e.target.value)} />
      {!goal && (
        <textarea style={{ minHeight: 90 }} placeholder={'Adımlar — her satıra bir tane\nA2 kursunu bitir\nB1 sınavına gir'}
          value={milestones} onChange={(e) => setMilestones(e.target.value)} />
      )}
      <div className="row">
        <button className="btn primary">Kaydet</button>
        <button type="button" className="btn" onClick={onCancel}>Vazgeç</button>
      </div>
    </form>
  )
}

function GoalDetail({ goal: g, run, onEdit, onDelete }) {
  const [step, setStep] = useState('')
  const add = (e) => {
    e.preventDefault()
    if (!step.trim()) return
    run(() => api.addMilestone(g.id, step.trim()))
    setStep('')
  }
  return (
    <section className="card" style={{ marginTop: 20 }}>
      <div className="row">
        <h2 style={{ margin: 0 }}>{CATEGORY_ICON[g.category]} {g.title}</h2>
        <span className="tag">{CATEGORY[g.category]}</span>
        <span className="tag goal">{STATUS[g.status]}</span>
        <div className="spacer" />
        <button className="btn" onClick={onEdit}>Düzenle</button>
        <button className="btn danger" onClick={onDelete}>Sil</button>
      </div>
      <p className="muted">
        {formatDate(g.startDate, { day: 'numeric', month: 'long', year: 'numeric' })} başladı
        {g.targetDate && ` · Hedef: ${formatDate(g.targetDate, { day: 'numeric', month: 'long', year: 'numeric' })}`}
      </p>
      {g.description && <p>{g.description}</p>}
      <div className="row" style={{ margin: '12px 0' }}>
        <div className="progress" style={{ flex: 1 }}><div style={{ width: `${g.progress}%` }} /></div>
        <strong>%{g.progress}</strong>
      </div>
      <h2>Adımlar</h2>
      <ul className="list">
        {g.milestones.length === 0 && <li className="empty">Henüz adım yok</li>}
        {g.milestones.map((m) => (
          <li key={m.id} className={`item ${m.done ? 'done' : ''}`}>
            <input type="checkbox" className="check" checked={m.done} onChange={() => run(() => api.setMilestoneDone(m.id, !m.done))} />
            <span className="title">{m.title}</span>
            <button className="btn ghost del" onClick={() => run(() => api.deleteMilestone(m.id))}>✕</button>
          </li>
        ))}
      </ul>
      <form className="row" onSubmit={add} style={{ marginTop: 8 }}>
        <input style={{ flex: 1 }} placeholder="Yeni adım ekle…" value={step} onChange={(e) => setStep(e.target.value)} />
        <button className="btn">Ekle</button>
      </form>
    </section>
  )
}
