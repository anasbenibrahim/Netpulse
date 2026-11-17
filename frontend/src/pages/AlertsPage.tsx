import { useState } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { Alert, AlertStatus } from '../api/types'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, Select, Table, td } from '../components/ui'
import { usePolling } from '../hooks/usePolling'
import { formatRelative } from '../lib/format'

type Filter = AlertStatus | 'ALL'

const button =
  'rounded-md border border-slate-300 bg-white px-2.5 py-1 text-xs font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50'

export function AlertsPage() {
  const [params, setParams] = useSearchParams()
  const status = (params.get('status') ?? 'ALL') as Filter
  const { data, error, refresh } = usePolling(() => api.alerts(status === 'ALL' ? undefined : status), [status])
  const [busyId, setBusyId] = useState<number>()
  const [actionError, setActionError] = useState<string>()

  async function act(alert: Alert, action: (id: number) => Promise<Alert>) {
    setBusyId(alert.id)
    setActionError(undefined)
    try {
      await action(alert.id)
      refresh()
    } catch (e) {
      setActionError(e instanceof Error ? e.message : String(e))
    } finally {
      setBusyId(undefined)
    }
  }

  return (
    <>
      <PageHeader title="Alerts" subtitle="Raised when an anomaly opens or escalates; closed automatically when it resolves">
        <Select<Filter>
          label="Status"
          value={status}
          onChange={(s) => setParams({ status: s })}
          options={[
            { value: 'ALL', label: 'All' },
            { value: 'OPEN', label: 'Open' },
            { value: 'ACKNOWLEDGED', label: 'Acknowledged' },
            { value: 'RESOLVED', label: 'Resolved' },
          ]}
        />
      </PageHeader>
      <ErrorBanner error={error ?? actionError} />
      <Card>
        {!data ? (
          <Loading />
        ) : data.length === 0 ? (
          <EmptyState>No alert.</EmptyState>
        ) : (
          <Table head={['Alert', 'Severity', 'Status', 'Raised', 'Actions']}>
            {data.map((a) => (
              <tr key={a.id} className="hover:bg-slate-50">
                <td className={td}>
                  <p className="font-medium text-slate-900">{a.title}</p>
                  <p className="text-xs text-slate-500">{a.message}</p>
                </td>
                <td className={td}><Badge value={a.severity} /></td>
                <td className={td}><Badge value={a.status} /></td>
                <td className={`${td} text-slate-500`}>{formatRelative(a.createdAt)}</td>
                <td className={td}>
                  <div className="flex gap-2">
                    {a.status === 'OPEN' && (
                      <button className={button} disabled={busyId === a.id} onClick={() => act(a, api.acknowledgeAlert)}>
                        Acknowledge
                      </button>
                    )}
                    {a.status !== 'RESOLVED' && (
                      <button className={button} disabled={busyId === a.id} onClick={() => act(a, api.resolveAlert)}>
                        Resolve
                      </button>
                    )}
                  </div>
                </td>
              </tr>
            ))}
          </Table>
        )}
      </Card>
    </>
  )
}
