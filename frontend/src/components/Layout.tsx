import { NavLink, Outlet } from 'react-router'

const NAV = [
  { to: '/', label: 'Overview' },
  { to: '/devices', label: 'Devices' },
  { to: '/telemetry', label: 'Telemetry' },
  { to: '/anomalies', label: 'Anomalies' },
  { to: '/alerts', label: 'Alerts' },
  { to: '/wifi', label: 'Wi-Fi' },
]

function linkClass({ isActive }: { isActive: boolean }) {
  return `block rounded-lg px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-teal-600 text-white' : 'text-slate-300 hover:bg-slate-800 hover:text-white'
  }`
}

export function Layout() {
  return (
    <div className="min-h-screen lg:flex">
      <aside className="bg-slate-900 lg:fixed lg:inset-y-0 lg:w-56">
        <div className="flex items-center gap-2 px-4 py-4">
          <img src="/favicon.svg" alt="" className="h-8 w-8" />
          <div>
            <p className="text-base font-semibold text-white">NetPulse</p>
            <p className="text-xs text-slate-400">IoT network monitoring</p>
          </div>
        </div>
        <nav className="flex gap-1 overflow-x-auto px-3 pb-3 lg:flex-col lg:overflow-visible">
          {NAV.map((item) => (
            <NavLink key={item.to} to={item.to} end={item.to === '/'} className={linkClass}>
              {item.label}
            </NavLink>
          ))}
        </nav>
        <p className="hidden px-4 pt-4 text-xs text-slate-500 lg:block">
          <span className="mr-1.5 inline-block h-2 w-2 animate-pulse rounded-full bg-emerald-400" />
          Live · refresh every 5 s
        </p>
      </aside>
      <main className="flex-1 px-4 py-6 sm:px-6 lg:ml-56 lg:px-8">
        <Outlet />
      </main>
    </div>
  )
}
