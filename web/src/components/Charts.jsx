// Bağımlılıksız küçük SVG grafikler: Sparkline (kart), LineChart ve BarChart (detay paneli).
import { useEffect, useRef, useState } from 'react'

// Grafikler kapsayıcının gerçek genişliğinde (1 birim = 1 px) çizilir; böylece yazılar her boyutta aynı kalır.
const H = 170
const PAD = { top: 12, right: 10, bottom: 22, left: 52 }

function useWidth() {
  const ref = useRef(null)
  const [width, setWidth] = useState(400)
  useEffect(() => {
    if (!ref.current) return
    const ro = new ResizeObserver(([e]) => setWidth(Math.max(Math.round(e.contentRect.width), 200)))
    ro.observe(ref.current)
    return () => ro.disconnect()
  }, [])
  return [ref, width]
}

export function Sparkline({ values, color = 'var(--accent)', height = 40 }) {
  if (!values?.length) return null
  const min = Math.min(...values), max = Math.max(...values), span = max - min || 1
  const pts = values.map((v, i) => `${(i / (values.length - 1)) * 200},${36 - ((v - min) / span) * 32}`).join(' ')
  return (
    <svg viewBox="0 0 200 40" width="100%" height={height} preserveAspectRatio="none" aria-hidden="true">
      <polyline fill="none" stroke={color} strokeWidth="2" strokeLinejoin="round" points={pts} vectorEffect="non-scaling-stroke" />
    </svg>
  )
}

function scale(points, key, W) {
  const vals = points.map((p) => p[key] ?? 0)
  const max = Math.max(...vals, 1)
  const innerW = W - PAD.left - PAD.right
  const innerH = H - PAD.top - PAD.bottom
  const x = (i) => PAD.left + (points.length === 1 ? innerW / 2 : (i / (points.length - 1)) * innerW)
  const y = (v) => PAD.top + innerH - (v / max) * innerH
  return { vals, max, x, y, innerW, innerH }
}

function Axes({ points, max, format, x, W }) {
  const label = (d) => new Date(d).toLocaleDateString('tr-TR', { day: 'numeric', month: 'short' })
  return (
    <>
      {[0, 0.5, 1].map((f) => {
        const yy = PAD.top + (H - PAD.top - PAD.bottom) * (1 - f)
        return (
          <g key={f}>
            <line x1={PAD.left} x2={W - PAD.right} y1={yy} y2={yy} stroke="var(--border)" strokeWidth="1" />
            <text x={PAD.left - 6} y={yy + 4} textAnchor="end" className="chart-label">{format(max * f)}</text>
          </g>
        )
      })}
      <text x={x(0)} y={H - 4} className="chart-label">{label(points[0].date)}</text>
      <text x={x(points.length - 1)} y={H - 4} textAnchor="end" className="chart-label">{label(points[points.length - 1].date)}</text>
    </>
  )
}

export function LineChart({ points, valueKey, color = 'var(--accent)', format = (v) => Math.round(v).toLocaleString('tr-TR') }) {
  const [ref, W] = useWidth()
  if (!points?.length) return null
  const { vals, max, x, y } = scale(points, valueKey, W)
  const path = vals.map((v, i) => `${i ? 'L' : 'M'}${x(i)},${y(v)}`).join(' ')
  const area = `${path} L${x(vals.length - 1)},${y(0)} L${x(0)},${y(0)} Z`
  return (
    <div ref={ref}>
    <svg viewBox={`0 0 ${W} ${H}`} width={W} height={H} className="chart" role="img">
      <Axes points={points} max={max} format={format} x={x} W={W} />
      <path d={area} fill={color} opacity="0.12" />
      <path d={path} fill="none" stroke={color} strokeWidth="2.5" strokeLinejoin="round" />
      {vals.map((v, i) => (
        <circle key={i} cx={x(i)} cy={y(v)} r="6" fill="transparent">
          <title>{`${new Date(points[i].date).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long' })}: ${format(v)}`}</title>
        </circle>
      ))}
      <circle cx={x(vals.length - 1)} cy={y(vals[vals.length - 1])} r="4" fill={color} />
    </svg>
    </div>
  )
}

export function BarChart({ points, valueKey, color = 'var(--accent-2)', format = (v) => Math.round(v).toLocaleString('tr-TR') }) {
  const [ref, W] = useWidth()
  if (!points?.length) return null
  const { vals, max, x, y, innerW } = scale(points, valueKey, W)
  const bw = Math.max(innerW / points.length - 3, 2)
  return (
    <div ref={ref}>
    <svg viewBox={`0 0 ${W} ${H}`} width={W} height={H} className="chart" role="img">
      <Axes points={points} max={max} format={format} x={x} W={W} />
      {vals.map((v, i) => (
        <rect key={i} x={x(i) - bw / 2} y={y(v)} width={bw} height={Math.max(y(0) - y(v), 1)} rx="2" fill={color}>
          <title>{`${new Date(points[i].date).toLocaleDateString('tr-TR', { day: 'numeric', month: 'long' })}: ${format(v)}`}</title>
        </rect>
      ))}
    </svg>
    </div>
  )
}
