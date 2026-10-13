/**
 * Halka grafik. slices: [{ key, label, value, color }]
 * Ortada başlık ve alt metin gösterilir.
 */
export default function Donut({ slices, size = 220, thickness = 26, title, subtitle }) {
  const total = slices.reduce((s, x) => s + x.value, 0)
  const r = (size - thickness) / 2
  const c = 2 * Math.PI * r
  const gap = slices.length > 1 ? 3 : 0
  let offset = 0

  return (
    <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} role="img" aria-label="Portföy dağılımı">
      <circle cx={size / 2} cy={size / 2} r={r} fill="none" stroke="var(--surface-2)" strokeWidth={thickness} />
      {total > 0 && slices.map((s) => {
        const len = (s.value / total) * c
        const el = (
          <circle key={s.key} cx={size / 2} cy={size / 2} r={r} fill="none" stroke={s.color} strokeWidth={thickness}
            strokeDasharray={`${Math.max(len - gap, 0.5)} ${c}`} strokeDashoffset={-offset}
            transform={`rotate(-90 ${size / 2} ${size / 2})`} style={{ filter: `drop-shadow(0 0 6px ${s.color}66)` }}>
            <title>{s.label}</title>
          </circle>
        )
        offset += len
        return el
      })}
      <text x="50%" y="47%" textAnchor="middle" className="donut-title">{title}</text>
      <text x="50%" y="59%" textAnchor="middle" className="donut-sub">{subtitle}</text>
    </svg>
  )
}
