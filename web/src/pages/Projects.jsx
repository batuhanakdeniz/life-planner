import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { fmtMoney0, fmtNum, timeAgo } from '../money.js'
import { BarChart, LineChart, Sparkline } from '../components/Charts.jsx'

const HEALTH = {
  OK: { label: 'Çalışıyor', cls: 'ok' },
  WARNING: { label: 'Uyarı', cls: 'warn' },
  DOWN: { label: 'Kesinti', cls: 'down' },
}
const SPARK_COLOR = { OK: 'var(--accent)', WARNING: 'var(--warn)', DOWN: 'var(--danger)' }

const pct = (v) => (v == null ? '' : `${v > 0 ? '+' : v < 0 ? '−' : ''}%${fmtNum(Math.abs(v), 1)}`)
const pctCls = (v) => (v > 0 ? 'pos' : v < 0 ? 'neg' : 'muted')
const uptime = (v) => `%${fmtNum(v, 2)}`

export default function Projects() {
  const [data, setData] = useState(null)
  const [selected, setSelected] = useState(null)
  const [error, setError] = useState(null)

  useEffect(() => {
    api.projects().then(setData).catch((e) => setError(e.message))
  }, [])

  if (!data) return error ? <div className="error">{error}</div> : <p className="muted">Yükleniyor…</p>
  const { totals: t, projects } = data
  const anyMock = projects.some((p) => p.mock)

  return (
    <>
      <div className="row">
        <h1>🚀 Projeler</h1>
        <div className="spacer" />
        {anyMock && <span className="tag overdue" title="Metrikler henüz gerçek kaynaklara bağlanmadı">örnek veri</span>}
        <span className="muted">Son güncelleme: {timeAgo(data.generatedAt)}</span>
      </div>
      <p className="subtitle">Projelerinin özet metrikleri. Ayrıntı için karta tıkla; her projenin kendi dashboard'u da bir tık uzakta.</p>
      {error && <div className="error" onClick={() => setError(null)}>{error}</div>}

      {projects.length === 0 ? (
        <section className="card empty-state">
          <h2>Takip edilen proje yok</h2>
          <p className="muted">Obsidian'da Projeler/Projeler.md tablosuna proje ekle. En geç 5 dakika içinde burada görünür.</p>
        </section>
      ) : (
        <>
          <div className="stats">
            <div className="card stat">
              <div className="label">Toplam aktif kullanıcı (bugün)</div>
              <div className="value">{fmtNum(t.activeUsers, 0)}</div>
              <div className={`stat-sub ${pctCls(t.activeUsersChangePct)}`}>{pct(t.activeUsersChangePct)} geçen haftaya göre</div>
            </div>
            <div className="card stat">
              <div className="label">Aylık aktif kullanıcı</div>
              <div className="value plain">{fmtNum(t.mau, 0)}</div>
              <div className="stat-sub">{t.projects} projenin toplamı</div>
            </div>
            <div className="card stat">
              <div className="label">Aylık gelir (MRR)</div>
              <div className="value plain">{fmtMoney0(t.mrr)}</div>
              <div className="stat-sub">gelir elde eden projeler</div>
            </div>
            <div className="card stat">
              <div className="label">Sağlık</div>
              <div className="value plain">{t.healthy} / {t.projects}</div>
              <div className={`stat-sub ${t.down ? 'neg' : t.warning ? 'warn' : 'pos'}`}>
                {t.down ? `${t.down} projede kesinti` : t.warning ? `${t.warning} projede uyarı` : 'Hepsi sağlıklı'}
              </div>
            </div>
          </div>

          <div className="project-grid">
            {projects.map((p) => (
              <ProjectCard key={p.slug} p={p} selected={selected === p.slug} onSelect={() => setSelected(selected === p.slug ? null : p.slug)} />
            ))}
          </div>

          {selected && <ProjectDetail slug={selected} onClose={() => setSelected(null)} onError={setError} />}
        </>
      )}
    </>
  )
}

function ProjectCard({ p, selected, onSelect }) {
  const h = HEALTH[p.health]
  return (
    <section className={`card project-card ${selected ? 'selected' : ''} health-${h.cls}`} onClick={onSelect} role="button" tabIndex={0}
      onKeyDown={(e) => e.key === 'Enter' && onSelect()}>
      <div className="row">
        <h2 style={{ margin: 0 }}>{p.name}</h2>
        <span className={`health ${h.cls}`}>{h.label}</span>
      </div>
      <p className="muted" style={{ margin: '4px 0 0' }}>{[p.description, p.platform].filter(Boolean).join(' · ')}</p>
      <div className="row" style={{ marginTop: 12, alignItems: 'baseline' }}>
        <span className="big-num">{fmtNum(p.activeUsers, 0)}</span>
        <span className="muted">aktif kullanıcı</span>
        <span className={pctCls(p.activeUsersChangePct)}>{pct(p.activeUsersChangePct)}</span>
      </div>
      <Sparkline values={p.sparkline} color={SPARK_COLOR[p.health]} />
      <div className="kv">
        <div>MAU<b>{fmtNum(p.mau, 0)}</b></div>
        <div>Yeni kayıt (7g)<b>{fmtNum(p.signups7d, 0)}</b></div>
        <div>MRR<b>{p.mrr != null ? fmtMoney0(p.mrr) : '—'}</b></div>
        <div>Uptime (30g)<b className={p.uptime30d < 99.5 ? 'warn' : ''}>{uptime(p.uptime30d)}</b></div>
        <div>Açık hata<b className={p.openIssues >= 10 ? 'neg' : ''}>{p.openIssues}</b></div>
        <div>Son deploy<b>{timeAgo(p.lastDeployAt)}</b></div>
      </div>
      {p.healthReasons.length > 0 && <p className={`reason ${h.cls}`}>{p.healthReasons[0]}{p.healthReasons.length > 1 ? ` (+${p.healthReasons.length - 1})` : ''}</p>}
      {p.dashboardUrl && (
        <a className="dash-link" href={p.dashboardUrl} target="_blank" rel="noreferrer" onClick={(e) => e.stopPropagation()}>Dashboard'u aç ↗</a>
      )}
    </section>
  )
}

function ProjectDetail({ slug, onClose, onError }) {
  const [d, setD] = useState(null)
  useEffect(() => {
    setD(null)
    api.project(slug).then(setD).catch((e) => onError(e.message))
    setTimeout(() => document.getElementById('project-detail')?.scrollIntoView({ behavior: 'smooth', block: 'start' }), 50)
  }, [slug, onError])

  if (!d) return <section id="project-detail" className="card" style={{ marginTop: 16 }}><p className="muted">Yükleniyor…</p></section>
  const s = d.summary
  const h = HEALTH[s.health]
  const hasRevenue = d.daily.some((x) => x.revenue != null)

  return (
    <section id="project-detail" className="card project-detail">
      <div className="row">
        <h2 style={{ margin: 0 }}>{s.name}</h2>
        <span className={`health ${h.cls}`}>{h.label}</span>
        {s.mock && <span className="tag overdue">örnek veri</span>}
        <div className="spacer" />
        {s.dashboardUrl && <a className="btn" href={s.dashboardUrl} target="_blank" rel="noreferrer">Dashboard'u aç ↗</a>}
        <button className="btn" onClick={onClose}>Kapat</button>
      </div>
      {s.healthReasons.length > 0 && (
        <ul className={`reasons ${h.cls}`}>{s.healthReasons.map((r) => <li key={r}>{r}</li>)}</ul>
      )}

      <div className="detail-charts">
        <div>
          <h3>Aktif kullanıcı · son 30 gün</h3>
          <LineChart points={d.daily} valueKey="activeUsers" color={SPARK_COLOR[s.health]} />
        </div>
        <div>
          <h3>Yeni kayıt · günlük</h3>
          <BarChart points={d.daily} valueKey="signups" />
        </div>
        {hasRevenue && (
          <div>
            <h3>Gelir · günlük</h3>
            <LineChart points={d.daily} valueKey="revenue" color="var(--success)" format={(v) => fmtMoney0(v)} />
          </div>
        )}
      </div>

      <table className="fin-table metrics-table">
        <tbody>
          <tr><td>Aktif kullanıcı (bugün)</td><td className="num">{fmtNum(s.activeUsers, 0)} <span className={pctCls(s.activeUsersChangePct)}>{pct(s.activeUsersChangePct)}</span></td></tr>
          <tr><td>Aylık aktif kullanıcı (MAU)</td><td className="num">{fmtNum(s.mau, 0)} <span className={pctCls(s.mauChangePct)}>{pct(s.mauChangePct)}</span></td></tr>
          <tr><td>Yeni kayıt (son 7 gün)</td><td className="num">{fmtNum(s.signups7d, 0)}</td></tr>
          <tr><td>Aylık gelir (MRR)</td><td className="num">{s.mrr != null ? <>{fmtMoney0(s.mrr)} <span className={pctCls(s.mrrChangePct)}>{pct(s.mrrChangePct)}</span></> : '—'}</td></tr>
          <tr><td>Erişilebilirlik (30 gün)</td><td className="num">{uptime(s.uptime30d)}</td></tr>
          <tr><td>Hata oranı (24 saat)</td><td className="num">%{fmtNum(s.errorRate24h, 1)}</td></tr>
          <tr><td>Açık hata</td><td className="num">{s.openIssues}</td></tr>
          <tr><td>Son deploy</td><td className="num">{new Date(s.lastDeployAt).toLocaleString('tr-TR', { dateStyle: 'medium', timeStyle: 'short' })} ({timeAgo(s.lastDeployAt)})</td></tr>
        </tbody>
      </table>
    </section>
  )
}
