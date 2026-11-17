// Mirrors the Spring Boot response records (netpulse-backend).

export type DeviceStatus = 'ONLINE' | 'OFFLINE'
export type NetworkStatus = 'EXCELLENT' | 'GOOD' | 'FAIR' | 'POOR' | 'CRITICAL' | 'UNKNOWN'
export type Severity = 'WARNING' | 'CRITICAL'
export type AnomalyStatus = 'ACTIVE' | 'RESOLVED'
export type AlertStatus = 'OPEN' | 'ACKNOWLEDGED' | 'RESOLVED'

export interface NetworkMetric {
  receivedAt: string
  ssid: string | null
  rssi: number | null
  gatewayLatency: number | null
  gatewayPacketLoss: number | null
  internetLatency: number | null
  internetPacketLoss: number | null
  healthScore: number | null
  networkStatus: NetworkStatus
}

export interface Telemetry {
  receivedAt: string
  uptimeSeconds: number | null
  freeHeap: number | null
  totalHeap: number | null
  heapUsage: number | null
  rssi: number | null
}

export interface Device {
  id: number
  deviceCode: string
  name: string
  status: DeviceStatus
  ipAddress: string | null
  ssid: string | null
  lastSeen: string | null
  createdAt: string
  activeAnomalies: number
  latestNetwork: NetworkMetric | null
}

export interface Anomaly {
  id: number
  deviceCode: string
  type: string
  label: string
  metricName: string
  unit: string
  severity: Severity
  status: AnomalyStatus
  detectedValue: number | null
  thresholdValue: number | null
  description: string
  occurrences: number
  firstDetectedAt: string
  lastDetectedAt: string
  resolvedAt: string | null
}

export interface Alert {
  id: number
  deviceCode: string
  anomalyId: number
  title: string
  message: string
  severity: Severity
  status: AlertStatus
  createdAt: string
  acknowledgedAt: string | null
  resolvedAt: string | null
}

export interface Overview {
  totalDevices: number
  onlineDevices: number
  offlineDevices: number
  activeAnomalies: number
  criticalAnomalies: number
  unresolvedAlerts: number
  recentAlerts: Alert[]
}

export type CommandType = 'WIFI_SCAN' | 'WIFI_CONNECT'
export type CommandStatus = 'SENT' | 'SUCCEEDED' | 'FAILED' | 'TIMED_OUT'

export interface WifiNetwork {
  ssid: string
  rssi: number
  channel: number
  security: string
}

export interface DeviceCommand {
  id: string
  deviceCode: string
  type: CommandType
  status: CommandStatus
  targetSsid: string | null
  message: string | null
  networks: WifiNetwork[] | null
  createdAt: string
  completedAt: string | null
}
