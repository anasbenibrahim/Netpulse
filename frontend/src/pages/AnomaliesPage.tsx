import { useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { AnomalyStatus } from '../api/types'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, Select, Table, td } from '../components/ui'
import { usePolling } from '../hooks/usePolling'
import { formatDateTime, formatNumber, formatRelative } from '../lib/format'

type Filter = AnomalyStatus | 'ALL'

export function AnomaliesPage() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? 'ACTIVE') as Filter
  const device = params.get('device') ?? ''
  const devices = usePolling(api.devices, [])
  const { data, error } = usePolling(() => api.anomalies(status === 'ALL' ? undefined : status, device || undefined), [status, device])

  const update = (next: Record<string, string>) => setParams({ status, ...(device && { device }), ...next })

  return (
    <>
      <PageHeader title="Anomalies" subtitle="Detected by threshold rules; resolved after 3 normal readings in a row">
        <Select
          label="Device"
          value={device}
          onChange={(d) => update({ device: d })}
          options={[{ value: '', label: 'All devices' }, ...(devices.data ?? []).map((d) => ({ value: d.deviceCode, label: d.name }))]}
        />
        <Select<Filter>
          label="Status"
          value={status}
          onChange={(s) => update({ status: s })}
          options={[
            { value: 'ACTIVE', label: 'Active' },
            { value: 'RESOLVED', label: 'Resolved' },
            { value: 'ALL', label: 'All' },
          ]}
        />
      </PageHeader>
      <ErrorBanner error={error} />
      <Card>
        {!data ? (
          <Loading />
        ) : data.length === 0 ? (
          <EmptyState>{status === 'ACTIVE' ? 'No active anomaly. The network looks healthy.' : 'No anomaly found.'}</EmptyState>
        ) : (
          <Table head={['Anomaly', 'Device', 'Severity', 'Status', 'Value', 'Threshold', 'Readings', 'First detected', 'Last detected']}>
            {data.map((a) => (
              <tr key={a.id} className="hover:bg-slate-50">
                <td className={td}>
                  <p className="font-medium text-slate-900">{a.label}</p>
                  <p className="text-xs text-slate-500">{a.description}</p>
                </td>
                <td className={td}>{a.deviceCode}</td>
                <td className={td}><Badge value={a.severity} /></td>
                <td className={td}><Badge value={a.status} /></td>
                <td className={`${td} tabular-nums`}>{formatNumber(a.detectedValue, 1, a.unit)}</td>
                <td className={`${td} tabular-nums text-slate-500`}>{formatNumber(a.thresholdValue, 1, a.unit)}</td>
                <td className={`${td} tabular-nums`}>{a.occurrences}</td>
                <td className={`${td} text-slate-500`}>{formatDateTime(a.firstDetectedAt)}</td>
                <td className={`${td} text-slate-500`}>
                  {a.status === 'RESOLVED' ? `resolved ${formatRelative(a.resolvedAt)}` : formatRelative(a.lastDetectedAt)}
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  )
}
