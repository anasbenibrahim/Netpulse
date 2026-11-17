import { useEffect, useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { api } from '../api/client'
import type { Device, DeviceCommand, WifiNetwork } from '../api/types'
import { Badge, Card, EmptyState, ErrorBanner, Loading, PageHeader, Select, Table, td } from '../components/ui'
import { useCommand } from '../hooks/useCommand'
import { usePolling } from '../hooks/usePolling'
import { formatNumber, formatRelative, rssiQuality } from '../lib/format'

const primaryButton =
  'rounded-lg bg-teal-600 px-4 py-2 text-sm font-medium text-white shadow-sm hover:bg-teal-700 disabled:cursor-not-allowed disabled:opacity-50'
const secondaryButton =
  'rounded-lg border border-slate-300 bg-white px-3 py-2 text-sm font-medium text-slate-700 shadow-sm hover:bg-slate-50 disabled:opacity-50'
const input =
  'w-full rounded-lg border border-slate-300 px-3 py-2 text-sm shadow-sm focus:border-teal-500 focus:ring-2 focus:ring-teal-500/20 focus:outline-none'

export function WifiPage() {
  const [params, setParams] = useSearchParams()
  const devices = usePolling(api.devices, [])
  const deviceCode = params.get('device') ?? ''
  const device = devices.data?.find((d) => d.deviceCode === deviceCode)

  useEffect(() => {
    if (!deviceCode && devices.data?.length) {
      setParams({ device: devices.data[0].deviceCode }, { replace: true })
    }
  }, [deviceCode, devices.data, setParams])

  const history = usePolling(() => (deviceCode ? api.commands(deviceCode) : Promise.resolve([])), [deviceCode])
  const scan = useCommand()
  const connect = useCommand()
  const [selected, setSelected] = useState<{ ssid: string; open: boolean }>()

  // A command that starts or finishes changes the history: refresh it right away.
  const { refresh } = history
  useEffect(() => {
    refresh()
  }, [scan.command?.status, connect.command?.status, refresh])

  const busy = scan.pending || connect.pending
  const networks = scan.command?.status === 'SUCCEEDED' ? (scan.command.networks ?? []) : []

  return (
    <>
      <PageHeader title="Wi-Fi" subtitle="Scan the networks around the ESP32 and move it to another Wi-Fi, without touching the code">
        {devices.data && devices.data.length > 0 && (
          <Select
            label="Device"
            value={deviceCode}
            onChange={(d) => {
              setParams({ device: d })
              scan.reset()
              connect.reset()
              setSelected(undefined)
            }}
            options={devices.data.map((d) => ({ value: d.deviceCode, label: d.name }))}
          />
        )}
      </PageHeader>
      <ErrorBanner error={devices.error} />

      {!devices.data ? (
        <Loading />
      ) : !device ? (
        <Card>
          <EmptyState>No device yet.</EmptyState>
        </Card>
      ) : (
        <div className="grid gap-6 xl:grid-cols-3">
          <div className="space-y-6 xl:col-span-2">
            <Card
              title="Nearby networks"
              actions={
                <button
                  className={primaryButton}
                  disabled={busy || device.status !== 'ONLINE'}
                  onClick={() => {
                    setSelected(undefined)
                    connect.reset()
                    void scan.start(() => api.scanWifi(device.deviceCode))
                  }}
                >
                  {scan.pending ? 'Scanning…' : 'Scan networks'}
                </button>
              }
            >
              {device.status !== 'ONLINE' && <p className="mb-3 text-sm text-red-600">The device is offline: it cannot scan.</p>}
              <CommandFeedback command={scan.command} error={scan.error} pendingText="The ESP32 is scanning (about 5 seconds)…" />

              {networks.length > 0 && (
                <ul className="divide-y divide-slate-100">
                  {networks.map((n) => (
                    <NetworkRow
                      key={n.ssid}
                      network={n}
                      current={n.ssid === device.ssid}
                      selected={selected?.ssid === n.ssid}
                      disabled={busy}
                      onSelect={() => setSelected({ ssid: n.ssid, open: n.security === 'OPEN' })}
                    />
                  ))}
                </ul>
              )}
              {!scan.command && !scan.error && (
                <EmptyState>Click “Scan networks” to list the Wi-Fi networks the ESP32 can see.</EmptyState>
              )}
              <button
                className="mt-3 text-sm text-teal-700 hover:underline disabled:opacity-50"
                disabled={busy}
                onClick={() => setSelected({ ssid: '', open: false })}
              >
                Hidden network? Enter its name manually
              </button>
            </Card>

            {selected && (
              <ConnectForm
                key={selected.ssid}
                initialSsid={selected.ssid}
                openNetwork={selected.open}
                disabled={busy || device.status !== 'ONLINE'}
                onCancel={() => setSelected(undefined)}
                onSubmit={(ssid, password) => {
                  void connect.start(() => api.connectWifi(device.deviceCode, ssid, password))
                  setSelected(undefined)
                }}
              />
            )}

            {(connect.command || connect.error) && (
              <Card title="Wi-Fi switch">
                <CommandFeedback
                  command={connect.command}
                  error={connect.error}
                  pendingText={`Switching to “${connect.command?.targetSsid}”… The ESP32 tests the network and the broker, and comes back automatically if it fails. This can take up to a minute.`}
                />
              </Card>
            )}
          </div>

          <div className="space-y-6">
            <CurrentNetwork device={device} />
            <Card title="Command history">
              {!history.data ? (
                <Loading />
              ) : history.data.length === 0 ? (
                <EmptyState>No command sent yet.</EmptyState>
              ) : (
                <Table head={['Command', 'Status', 'When']}>
                  {history.data.map((c) => (
                    <tr key={c.id}>
                      <td className={td}>
                        <p className="font-medium text-slate-800">
                          {c.type === 'WIFI_SCAN' ? 'Scan' : `Connect to ${c.targetSsid}`}
                        </p>
                        {c.message && <p className="max-w-56 truncate text-xs text-slate-500" title={c.message}>{c.message}</p>}
                      </td>
                      <td className={td}><Badge value={c.status} /></td>
                      <td className={`${td} text-slate-500`}>{formatRelative(c.createdAt)}</td>
                    </tr>
                  ))}
                </Table>
              )}
            </Card>
          </div>
        </div>
      )}
    </>
  )
}

function CurrentNetwork({ device }: { device: Device }) {
  const n = device.latestNetwork
  return (
    <Card title="Current network" actions={<Badge value={device.status} />}>
      <dl className="grid grid-cols-2 gap-x-4 gap-y-3 text-sm">
        <dt className="text-slate-500">Wi-Fi</dt>
        <dd className="font-medium text-slate-900">{device.ssid ?? 'N/A'}</dd>
        <dt className="text-slate-500">IP address</dt>
        <dd className="font-mono text-xs text-slate-900">{device.ipAddress ?? 'N/A'}</dd>
        <dt className="text-slate-500">Signal</dt>
        <dd className="text-slate-900">
          {formatNumber(n?.rssi, 0, 'dBm')} <span className="text-xs text-slate-400">{rssiQuality(n?.rssi)}</span>
        </dd>
        <dt className="text-slate-500">Health</dt>
        <dd className="flex items-center gap-2 text-slate-900">
          {formatNumber(n?.healthScore, 0)}/100 {n && <Badge value={n.networkStatus} />}
        </dd>
      </dl>
    </Card>
  )
}

function SignalBars({ rssi }: { rssi: number }) {
  const level = rssi >= -55 ? 4 : rssi >= -67 ? 3 : rssi >= -78 ? 2 : 1
  return (
    <span className="inline-flex items-end gap-0.5" aria-label={`${rssi} dBm`}>
      {[1, 2, 3, 4].map((bar) => (
        <span
          key={bar}
          className={`w-1 rounded-sm ${bar <= level ? 'bg-teal-600' : 'bg-slate-200'}`}
          style={{ height: `${bar * 4}px` }}
        />
      ))}
    </span>
  )
}

function NetworkRow({ network, current, selected, disabled, onSelect }: {
  network: WifiNetwork
  current: boolean
  selected: boolean
  disabled: boolean
  onSelect: () => void
}) {
  return (
    <li className={`flex flex-wrap items-center justify-between gap-3 py-3 ${selected ? 'bg-teal-50/60' : ''}`}>
      <div className="flex items-center gap-3">
        <SignalBars rssi={network.rssi} />
        <div>
          <p className="font-medium text-slate-900">
            {network.ssid}
            {current && <span className="ml-2 rounded bg-emerald-50 px-1.5 py-0.5 text-xs font-medium text-emerald-700">connected</span>}
          </p>
          <p className="text-xs text-slate-500">
            {network.rssi} dBm · channel {network.channel} · {network.security === 'OPEN' ? 'open network' : `🔒 ${network.security}`}
          </p>
        </div>
      </div>
      {!current && (
        <button className={secondaryButton} disabled={disabled} onClick={onSelect}>
          Connect
        </button>
      )}
    </li>
  )
}

function ConnectForm({ initialSsid, openNetwork, disabled, onCancel, onSubmit }: {
  initialSsid: string
  openNetwork: boolean
  disabled: boolean
  onCancel: () => void
  onSubmit: (ssid: string, password: string) => void
}) {
  const [ssid, setSsid] = useState(initialSsid)
  const [password, setPassword] = useState('')
  const [showPassword, setShowPassword] = useState(false)

  const passwordError = !openNetwork && password.length > 0 && password.length < 8 ? 'At least 8 characters' : undefined
  const canSubmit = ssid.trim().length > 0 && (openNetwork || password.length >= 8) && !disabled

  function submit(e: FormEvent) {
    e.preventDefault()
    if (canSubmit) onSubmit(ssid.trim(), openNetwork ? '' : password)
  }

  return (
    <Card title={initialSsid ? `Connect to “${initialSsid}”` : 'Connect to a hidden network'}>
      <form onSubmit={submit} className="space-y-4" autoComplete="off">
        {!initialSsid && (
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Network name (SSID)</span>
            <input className={input} value={ssid} maxLength={32} onChange={(e) => setSsid(e.target.value)} autoFocus />
          </label>
        )}
        {openNetwork ? (
          <p className="text-sm text-slate-600">Open network: no password needed.</p>
        ) : (
          <label className="block text-sm">
            <span className="mb-1 block font-medium text-slate-700">Password</span>
            <div className="flex gap-2">
              <input
                className={input}
                type={showPassword ? 'text' : 'password'}
                value={password}
                maxLength={63}
                autoComplete="new-password"
                autoFocus={Boolean(initialSsid)}
                onChange={(e) => setPassword(e.target.value)}
              />
              <button type="button" className={secondaryButton} onClick={() => setShowPassword((v) => !v)}>
                {showPassword ? 'Hide' : 'Show'}
              </button>
            </div>
            {passwordError && <span className="mt-1 block text-xs text-red-600">{passwordError}</span>}
          </label>
        )}
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-xs text-amber-800">
          The ESP32 keeps the new network only if it can reach the MQTT broker from it. Otherwise it goes back to its
          current network by itself. Only join networks you are allowed to use.
        </p>
        <div className="flex gap-2">
          <button type="submit" className={primaryButton} disabled={!canSubmit}>
            Connect the ESP32
          </button>
          <button type="button" className={secondaryButton} onClick={onCancel}>
            Cancel
          </button>
        </div>
      </form>
    </Card>
  )
}

function CommandFeedback({ command, error, pendingText }: { command?: DeviceCommand; error?: string; pendingText: string }) {
  if (error) {
    return <p className="mb-3 rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">{error}</p>
  }
  if (!command) return null
  if (command.status === 'SENT') {
    return (
      <p className="mb-3 flex items-center gap-2 rounded-lg bg-sky-50 px-3 py-2 text-sm text-sky-800">
        <span className="h-3 w-3 animate-spin rounded-full border-2 border-sky-300 border-t-sky-700" />
        {pendingText}
      </p>
    )
  }
  const tone =
    command.status === 'SUCCEEDED' ? 'bg-emerald-50 text-emerald-800' : command.status === 'FAILED' ? 'bg-red-50 text-red-700' : 'bg-amber-50 text-amber-800'
  return (
    <p className={`mb-3 rounded-lg px-3 py-2 text-sm ${tone}`}>
      <Badge value={command.status} /> <span className="ml-1">{command.message}</span>
    </p>
  )
}
