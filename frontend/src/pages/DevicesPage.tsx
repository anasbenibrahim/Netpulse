import { Link } from 'react-router'
import { api } from '../api/client'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, Table, td } from '../components/ui'
import { usePolling } from '../hooks/usePolling'
import { formatNumber, formatRelative, rssiQuality } from '../lib/format'

export function DevicesPage() {
  const { data, error } = usePolling(api.devices, [])

  return (
    <>
      <PageHeader title="Devices" subtitle="ESP32 boards register automatically on their first MQTT message" />
      <ErrorBanner error={error} />
      <Card>
        {!data ? (
          <Loading />
        ) : data.length === 0 ? (
          <EmptyState>No device registered yet.</EmptyState>
        ) : (
          <Table head={['Device', 'Status', 'IP address', 'Wi-Fi', 'Signal', 'Health', 'Internet latency', 'Anomalies', 'Last seen']}>
            {data.map((d) => {
              const n = d.latestNetwork
              return (
                <tr key={d.id} className="hover:bg-slate-50">
                  <td className={td}>
                    <Link to={`/telemetry?device=${encodeURIComponent(d.deviceCode)}`} className="font-medium text-teal-700 hover:underline">
                      {d.name}
                    </Link>
                  </td>
                  <td className={td}><Badge value={d.status} /></td>
                  <td className={`${td} font-mono text-xs`}>{d.ipAddress ?? 'N/A'}</td>
                  <td className={td}>{d.ssid ?? 'N/A'}</td>
                  <td className={`${td} tabular-nums`}>
                    {formatNumber(n?.rssi, 0, 'dBm')} <span className="text-xs text-slate-400">{rssiQuality(n?.rssi)}</span>
                  </td>
                  <td className={td}>
                    <span className="mr-2 tabular-nums">{formatNumber(n?.healthScore, 0)}</span>
                    {n && <Badge value={n.networkStatus} />}
                  </td>
                  <td className={`${td} tabular-nums`}>{formatNumber(n?.internetLatency, 1, 'ms')}</td>
                  <td className={td}>
                    {d.activeAnomalies > 0 ? (
                      <Link to={`/anomalies?device=${encodeURIComponent(d.deviceCode)}`} className="font-medium text-red-600 hover:underline">
                        {d.activeAnomalies} active
                      </Link>
                    ) : (
                      <span className="text-slate-400">None</span>
                    )}
                  </td>
                  <td className={`${td} text-slate-500`}>{formatRelative(d.lastSeen)}</td>
                </tr>
              )
            })}
          </Table>
        )}
      </Card>
    </>
  )
}
