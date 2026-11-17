import type { Alert, AlertStatus, Anomaly, AnomalyStatus, Device, DeviceCommand, NetworkMetric, Overview, Telemetry } from './types'

// Same origin: Vite proxies /api to Spring Boot in dev (see vite.config.ts).
const BASE = import.meta.env.VITE_API_BASE ?? '/api'

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(BASE + path, init)
  if (!response.ok) {
    let detail = response.statusText
    try {
      detail = (await response.json()).detail ?? detail
    } catch {
      // body was not JSON
    }
    throw new Error(`${response.status} ${detail}`)
  }
  return response.json() as Promise<T>
}

function query(params: Record<string, string | number | undefined>): string {
  const entries = Object.entries(params).filter(([, v]) => v !== undefined && v !== '')
  return entries.length ? '?' + new URLSearchParams(entries.map(([k, v]) => [k, String(v)])) : ''
}

export const api = {
  overview: () => request<Overview>('/overview'),
  devices: () => request<Device[]>('/devices'),
  telemetry: (deviceCode: string, minutes: number) =>
    request<Telemetry[]>(`/devices/${encodeURIComponent(deviceCode)}/telemetry${query({ minutes })}`),
  network: (deviceCode: string, minutes: number) =>
    request<NetworkMetric[]>(`/devices/${encodeURIComponent(deviceCode)}/network${query({ minutes })}`),
  anomalies: (status?: AnomalyStatus, device?: string) => request<Anomaly[]>(`/anomalies${query({ status, device })}`),
  alerts: (status?: AlertStatus) => request<Alert[]>(`/alerts${query({ status })}`),
  acknowledgeAlert: (id: number) => request<Alert>(`/alerts/${id}/acknowledge`, { method: 'POST' }),
  resolveAlert: (id: number) => request<Alert>(`/alerts/${id}/resolve`, { method: 'POST' }),
  scanWifi: (deviceCode: string) =>
    request<DeviceCommand>(`/devices/${encodeURIComponent(deviceCode)}/wifi/scan`, { method: 'POST' }),
  connectWifi: (deviceCode: string, ssid: string, password: string) =>
    request<DeviceCommand>(`/devices/${encodeURIComponent(deviceCode)}/wifi/connect`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ ssid, password }),
    }),
  command: (id: string) => request<DeviceCommand>(`/commands/${encodeURIComponent(id)}`),
  commands: (deviceCode: string) => request<DeviceCommand[]>(`/devices/${encodeURIComponent(deviceCode)}/commands`),
}
