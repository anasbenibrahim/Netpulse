import { useEffect } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../api/client'
import { MetricChart } from '../components/MetricChart'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, Select, StatCard } from '../components/ui'
import { usePolling } from '../hooks/usePolling'
import { formatBytes, formatDuration, formatNumber } from '../lib/format'

const WINDOWS = [
  { value: '15', label: 'Last 15 min' },
  { value: '60', label: 'Last hour' },
  { value: '360', label: 'Last 6 hours' },
  { value: '1440', label: 'Last 24 hours' },
]

// Mirrors the default thresholds in application.properties (netpulse.anomaly.*).
const THRESHOLDS = { rssi: -80, internetLatency: 300, packetLoss: 10, healthScore: 60, freeHeap: 20000 }

export function TelemetryPage() {
  const [params, setParams] = useSearchParams()
  const devices = usePolling(api.devices, [])
  const deviceCode = params.get('device') ?? ''
  const minutes = params.get('minutes') ?? '60'

  // Pick the first device when none is selected yet.
  useEffect(() => {
    if (!deviceCode && devices.data?.length) {
      setParams({ device: devices.data[0].deviceCode, minutes }, { replace: true })
    }
  }, [deviceCode, devices.data, minutes, setParams])

  const network = usePolling(() => (deviceCode ? api.network(deviceCode, Number(minutes)) : Promise.resolve([])), [deviceCode, minutes])
  const telemetry = usePolling(() => (deviceCode ? api.telemetry(deviceCode, Number(minutes)) : Promise.resolve([])), [deviceCode, minutes])

  const latestNet = network.data?.at(-1)
  const latestSys = telemetry.data?.at(-1)

  return (
    <>
      <PageHeader title="Telemetry" subtitle="Measurements sent by the ESP32 every ~5 seconds">
        {devices.data && devices.data.length > 0 && (
          <Select
            label="Device"
            value={deviceCode}
            onChange={(device) => setParams({ device, minutes })}
            options={devices.data.map((d) => ({ value: d.deviceCode, label: d.name }))}
          />
        )}
        <Select label="Window" value={minutes} onChange={(m) => setParams({ device: deviceCode, minutes: m })} options={WINDOWS} />
      </PageHeader>
      <ErrorBanner error={devices.error ?? network.error ?? telemetry.error} />

      {!devices.data ? (
        <Loading />
      ) : devices.data.length === 0 ? (
        <Card>
          <EmptyState>No device yet. Telemetry appears as soon as an ESP32 publishes.</EmptyState>
        </Card>
      ) : (
        <>
          <div className="grid grid-cols-2 gap-4 md:grid-cols-3 xl:grid-cols-6">
            <StatCard label="Wi-Fi signal" value={formatNumber(latestNet?.rssi, 0)} hint="dBm" />
            <StatCard
              label="Health score"
              value={formatNumber(latestNet?.healthScore, 0)}
              hint={latestNet ? latestNet.networkStatus.toLowerCase() : undefined}
            />
            <StatCard label="Internet latency" value={formatNumber(latestNet?.internetLatency, 0)} hint="ms" />
            <StatCard label="Packet loss" value={formatNumber(latestNet?.internetPacketLoss, 0)} hint="% to internet" />
            <StatCard label="Free heap" value={formatBytes(latestSys?.freeHeap)} hint={`${formatNumber(latestSys?.heapUsage, 1)} % used`} />
            <StatCard label="Uptime" value={formatDuration(latestSys?.uptimeSeconds)} />
          </div>

          {network.loading && !network.data ? (
            <Loading />
          ) : network.data?.length === 0 ? (
            <Card className="mt-6">
              <EmptyState>No reading in this time window.</EmptyState>
            </Card>
          ) : (
            <div className="mt-6 grid gap-6 xl:grid-cols-2">
              <Card title="Wi-Fi signal (RSSI)" actions={latestNet && <Badge value={latestNet.networkStatus} />}>
                <MetricChart
                  data={network.data ?? []}
                  unit="dBm"
                  series={[{ key: 'rssi', label: 'RSSI', color: '#0d9488' }]}
                  threshold={{ value: THRESHOLDS.rssi, label: 'weak' }}
                />
              </Card>
              <Card title="Network health score">
                <MetricChart
                  data={network.data ?? []}
                  unit="/100"
                  domain={[0, 100]}
                  series={[{ key: 'healthScore', label: 'Score', color: '#7c3aed' }]}
                  threshold={{ value: THRESHOLDS.healthScore, label: 'degraded' }}
                />
              </Card>
              <Card title="Latency">
                <MetricChart
                  data={network.data ?? []}
                  unit="ms"
                  series={[
                    { key: 'gatewayLatency', label: 'Gateway', color: '#0284c7' },
                    { key: 'internetLatency', label: 'Internet', color: '#ea580c' },
                  ]}
                  threshold={{ value: THRESHOLDS.internetLatency, label: 'internet threshold' }}
                />
              </Card>
              <Card title="Packet loss">
                <MetricChart
                  data={network.data ?? []}
                  unit="%"
                  domain={[0, 100]}
                  series={[
                    { key: 'gatewayPacketLoss', label: 'Gateway', color: '#0284c7' },
                    { key: 'internetPacketLoss', label: 'Internet', color: '#ea580c' },
                  ]}
                  threshold={{ value: THRESHOLDS.packetLoss, label: 'threshold' }}
                />
              </Card>
              <Card title="Free heap memory">
                <MetricChart
                  data={telemetry.data ?? []}
                  unit="bytes"
                  series={[{ key: 'freeHeap', label: 'Free heap', color: '#16a34a' }]}
                  threshold={{ value: THRESHOLDS.freeHeap, label: 'low' }}
                />
              </Card>
              <Card title="Heap usage">
                <MetricChart
                  data={telemetry.data ?? []}
                  unit="%"
                  domain={[0, 100]}
                  series={[{ key: 'heapUsage', label: 'Heap usage', color: '#db2777' }]}
                />
              </Card>
            </div>
          )}
        </>
      )}
    </>
  )
}
