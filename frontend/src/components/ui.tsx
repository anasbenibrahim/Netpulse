import type { ReactNode } from 'react'

type Tone = 'green' | 'red' | 'amber' | 'blue' | 'slate' | 'teal'

const TONES: Record<Tone, string> = {
  green: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  red: 'bg-red-50 text-red-700 ring-red-600/20',
  amber: 'bg-amber-50 text-amber-800 ring-amber-600/20',
  blue: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  slate: 'bg-slate-100 text-slate-600 ring-slate-500/20',
  teal: 'bg-teal-50 text-teal-700 ring-teal-600/20',
}

// One place to decide the colour of every status the backend returns.
const STATUS_TONE: Record<string, Tone> = {
  ONLINE: 'green',
  OFFLINE: 'red',
  EXCELLENT: 'green',
  GOOD: 'teal',
  FAIR: 'amber',
  POOR: 'red',
  CRITICAL: 'red',
  UNKNOWN: 'slate',
  WARNING: 'amber',
  ACTIVE: 'red',
  RESOLVED: 'slate',
  OPEN: 'red',
  ACKNOWLEDGED: 'blue',
  SENT: 'blue',
  SUCCEEDED: 'green',
  FAILED: 'red',
  TIMED_OUT: 'amber',
}

export function Badge({ value }: { value: string }) {
  const tone = TONES[STATUS_TONE[value] ?? 'slate']
  return (
    <span className={`inline-flex items-center rounded-md px-2 py-0.5 text-xs font-medium ring-1 ring-inset ${tone}`}>
      {(value.charAt(0) + value.slice(1).toLowerCase()).replace('_', ' ')}
    </span>
  )
}

export function Card({ title, actions, children, className = '' }: {
  title?: string
  actions?: ReactNode
  children: ReactNode
  className?: string
}) {
  return (
    <section className={`rounded-xl border border-slate-200 bg-white shadow-sm ${className}`}>
      {(title || actions) && (
        <header className="flex flex-wrap items-center justify-between gap-2 border-b border-slate-100 px-4 py-3">
          {title && <h2 className="text-sm font-semibold text-slate-700">{title}</h2>}
          {actions}
        </header>
      )}
      <div className="p-4">{children}</div>
    </section>
  )
}

export function StatCard({ label, value, hint, tone = 'slate' }: {
  label: string
  value: ReactNode
  hint?: string
  tone?: 'slate' | 'green' | 'red' | 'amber'
}) {
  const accent = { slate: 'text-slate-900', green: 'text-emerald-600', red: 'text-red-600', amber: 'text-amber-600' }[tone]
  return (
    <div className="rounded-xl border border-slate-200 bg-white p-4 shadow-sm">
      <p className="text-xs font-medium uppercase tracking-wide text-slate-500">{label}</p>
      <p className={`mt-1 text-3xl font-semibold tabular-nums ${accent}`}>{value}</p>
      {hint && <p className="mt-1 text-xs text-slate-500">{hint}</p>}
    </div>
  )
}

export function PageHeader({ title, subtitle, children }: { title: string; subtitle?: string; children?: ReactNode }) {
  return (
    <div className="mb-6 flex flex-wrap items-end justify-between gap-4">
      <div>
        <h1 className="text-2xl font-semibold text-slate-900">{title}</h1>
        {subtitle && <p className="mt-1 text-sm text-slate-500">{subtitle}</p>}
      </div>
      {children && <div className="flex flex-wrap items-center gap-2">{children}</div>}
    </div>
  )
}

export function ErrorBanner({ error }: { error?: string }) {
  if (!error) return null
  return (
    <div className="mb-4 rounded-lg border border-red-200 bg-red-50 px-4 py-3 text-sm text-red-700">
      Backend unreachable: {error}. Is Spring Boot running on port 7000?
    </div>
  )
}

export function EmptyState({ children }: { children: ReactNode }) {
  return <p className="py-8 text-center text-sm text-slate-500">{children}</p>
}

export function Loading() {
  return <p className="py-8 text-center text-sm text-slate-400">Loading…</p>
}

export function Select<T extends string>({ value, onChange, options, label }: {
  value: T
  onChange: (value: T) => void
  options: { value: T; label: string }[]
  label: string
}) {
  return (
    <label className="flex items-center gap-2 text-sm text-slate-600">
      <span className="sr-only sm:not-sr-only">{label}</span>
      <select
        className="rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-sm text-slate-800 shadow-sm focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 focus:outline-none"
        value={value}
        onChange={(e) => onChange(e.target.value as T)}
      >
        {options.map((o) => (
          <option key={o.value} value={o.value}>
            {o.label}
          </option>
        ))}
      </select>
    </label>
  )
}

/** Horizontally scrollable table wrapper so wide tables never break the page on phones. */
export function Table({ head, children }: { head: string[]; children: ReactNode }) {
  return (
    <div className="-mx-4 overflow-x-auto">
      <table className="min-w-full text-left text-sm">
        <thead>
          <tr className="border-b border-slate-200 text-xs uppercase tracking-wide text-slate-500">
            {head.map((h) => (
              <th key={h} className="px-4 py-2 font-medium whitespace-nowrap">
                {h}
              </th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-slate-100">{children}</tbody>
      </table>
    </div>
  )
}

export const td = 'px-4 py-2.5 whitespace-nowrap'
