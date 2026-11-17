import { Link } from 'react-router'
import { api } from '../api/client'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, StatCard } from '../components/ui'
import { usePolling } from '../hooks/usePolling'
import { formatNumber, formatRelative } from '../lib/format'

export function OverviewPage() {
  const overview = usePolling(api.overview, [])
  const devices = usePolling(api.devices, [])
  const o = overview.data

  return (
    <>
      <PageHeader title="Overview" subtitle="State of every ESP32 and of the network it measures" />
      <ErrorBanner error={overview.error ?? devices.error} />

      {!o ? (
        <Loading />
      ) : (
        <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
          <StatCard label="Devices" value={o.totalDevices} />
          <StatCard label="Online" value={o.onlineDevices} tone="green" />
          <StatCard label="Offline" value={o.offlineDevices} tone={o.offlineDevices ? 'red' : 'slate'} />
          <StatCard label="Active anomalies" value={o.activeAnomalies} tone={o.activeAnomalies ? 'amber' : 'slate'} />
          <StatCard label="Critical" value={o.criticalAnomalies} tone={o.criticalAnomalies ? 'red' : 'slate'} />
          <StatCard label="Unresolved alerts" value={o.unresolvedAlerts} tone={o.unresolvedAlerts ? 'red' : 'slate'} />
        </div>
      )}

      <div className="mt-6 grid gap-6 xl:grid-cols-2">
        <Card title="Devices" actions={<Link to="/devices" className="text-sm text-teal-700 hover:underline">All devices</Link>}>
          {!devices.data ? (
            <Loading />
          ) : devices.data.length === 0 ? (
            <EmptyState>No device yet. Power on the ESP32: it registers itself on its first MQTT message.</EmptyState>
          ) : (
            <ul className="divide-y divide-slate-100">
              {devices.data.map((d) => (
                <li key={d.id} className="flex flex-wrap items-center justify-between gap-2 py-3">
                  <div>
                    <Link to={`/telemetry?device=${encodeURIComponent(d.deviceCode)}`} className="font-medium text-slate-900 hover:underline">
                      {d.name}
                    </Link>
                    <p className="text-xs text-slate-500">
                      {d.ipAddress ?? 'no IP'} · seen {formatRelative(d.lastSeen)}
                    </p>
                  </div>
                  <div className="flex items-center gap-2">
                    <span className="text-sm tabular-nums text-slate-600">
                      {formatNumber(d.latestNetwork?.healthScore, 0)}/100
                    </span>
                    {d.latestNetwork && <Badge value={d.latestNetwork.networkStatus} />}
                    <Badge value={d.status} />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card title="Recent alerts" actions={<Link to="/alerts" className="text-sm text-teal-700 hover:underline">All alerts</Link>}>
          {!o ? (
            <Loading />
          ) : o.recentAlerts.length === 0 ? (
            <EmptyState>No alert. Everything looks healthy.</EmptyState>
          ) : (
            <ul className="divide-y divide-slate-100">
              {o.recentAlerts.map((a) => (
                <li key={a.id} className="py-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-medium text-slate-900">{a.title}</p>
                    <div className="flex gap-2">
                      <Badge value={a.severity} />
                      <Badge value={a.status} />
                    </div>
                  </div>
                  <p className="mt-0.5 text-sm text-slate-600">{a.message}</p>
                  <p className="mt-0.5 text-xs text-slate-400">{formatRelative(a.createdAt)}</p>
                </li>
              ))}
            </ul>
          )}
        </Card>
      </div>
    </>
  )
}
