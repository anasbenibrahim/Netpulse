export function formatNumber(value: number | null | undefined, digits = 1, unit = ''): string {
  if (value === null || value === undefined) return 'N/A'
  const text = Number.isInteger(value) ? String(value) : value.toFixed(digits)
  return unit ? `${text} ${unit}` : text
}

export function formatBytes(value: number | null | undefined): string {
  if (value === null || value === undefined) return 'N/A'
  if (value >= 1024 * 1024) return `${(value / 1024 / 1024).toFixed(1)} MB`
  if (value >= 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${value} B`
}

export function formatDuration(totalSeconds: number | null | undefined): string {
  if (totalSeconds === null || totalSeconds === undefined) return 'N/A'
  const d = Math.floor(totalSeconds / 86400)
  const h = Math.floor((totalSeconds % 86400) / 3600)
  const m = Math.floor((totalSeconds % 3600) / 60)
  const s = Math.floor(totalSeconds % 60)
  if (d) return `${d}d ${h}h`
  if (h) return `${h}h ${m}m`
  if (m) return `${m}m ${s}s`
  return `${s}s`
}

export function formatDateTime(iso: string | null | undefined): string {
  if (!iso) return 'N/A'
  return new Date(iso).toLocaleString()
}

export function formatTime(iso: string): string {
  return new Date(iso).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })
}

/** "12 s ago", "3 min ago"... */
export function formatRelative(iso: string | null | undefined, now = Date.now()): string {
  if (!iso) return 'never'
  const seconds = Math.max(0, Math.round((now - new Date(iso).getTime()) / 1000))
  if (seconds < 60) return `${seconds} s ago`
  if (seconds < 3600) return `${Math.floor(seconds / 60)} min ago`
  if (seconds < 86400) return `${Math.floor(seconds / 3600)} h ago`
  return `${Math.floor(seconds / 86400)} d ago`
}

/** Matches the firmware's RSSI score bands. */
export function rssiQuality(rssi: number | null | undefined): string {
  if (rssi === null || rssi === undefined) return 'N/A'
  if (rssi >= -50) return 'Excellent'
  if (rssi >= -60) return 'Good'
  if (rssi >= -70) return 'Fair'
  if (rssi >= -80) return 'Weak'
  return 'Very weak'
}
