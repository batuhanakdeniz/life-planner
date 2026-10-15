import { useRef, useState } from 'react'

/**
 * Ortak dosya seçme / sürükle-bırak alanı. Yüklemeyi kendisi yapmaz; seçilen dosyayı onFile'a verir.
 * Örnek: <FileDrop accept=".pdf" label="Hesap özeti PDF'ini bırak" onFile={(f) => api.uploadStatement(f)} busy={yukleniyor} />
 */
export default function FileDrop({ accept, label, hint, onFile, busy = false, maxMb = 20 }) {
  const input = useRef(null)
  const [over, setOver] = useState(false)
  const [error, setError] = useState(null)

  const pick = (file) => {
    if (!file) return
    setError(null)
    const exts = accept?.split(',').map((x) => x.trim().toLowerCase()) ?? []
    if (exts.length && !exts.some((e) => file.name.toLowerCase().endsWith(e))) {
      setError(`Yalnızca ${exts.join(', ')} dosyaları`)
      return
    }
    if (file.size > maxMb * 1024 * 1024) {
      setError(`Dosya çok büyük (en fazla ${maxMb} MB)`)
      return
    }
    onFile(file)
  }

  return (
    <div className={`file-drop ${over ? 'over' : ''} ${busy ? 'busy' : ''}`}
      onDragOver={(e) => { e.preventDefault(); setOver(true) }}
      onDragLeave={() => setOver(false)}
      onDrop={(e) => { e.preventDefault(); setOver(false); if (!busy) pick(e.dataTransfer.files?.[0]) }}
      onClick={() => !busy && input.current?.click()}
      role="button" tabIndex={0} onKeyDown={(e) => e.key === 'Enter' && !busy && input.current?.click()}>
      <input ref={input} type="file" accept={accept} hidden onChange={(e) => { pick(e.target.files?.[0]); e.target.value = '' }} />
      <div className="file-drop-icon">{busy ? '⏳' : '📄'}</div>
      <div className="file-drop-label">{busy ? 'Yükleniyor ve okunuyor…' : label}</div>
      {hint && !busy && <div className="muted">{hint}</div>}
      {error && <div className="neg small">{error}</div>}
    </div>
  )
}
