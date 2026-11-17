import { BrowserRouter, Route, Routes } from 'react-router'
import { Layout } from './components/Layout'
import { AlertsPage } from './pages/AlertsPage'
import { AnomaliesPage } from './pages/AnomaliesPage'
import { DevicesPage } from './pages/DevicesPage'
import { OverviewPage } from './pages/OverviewPage'
import { TelemetryPage } from './pages/TelemetryPage'
import { WifiPage } from './pages/WifiPage'

export function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route element={<Layout />}>
          <Route index element={<OverviewPage />} />
          <Route path="devices" element={<DevicesPage />} />
          <Route path="telemetry" element={<TelemetryPage />} />
          <Route path="anomalies" element={<AnomaliesPage />} />
          <Route path="alerts" element={<AlertsPage />} />
          <Route path="wifi" element={<WifiPage />} />
        </Route>
      </Routes>
    </BrowserRouter>
  )
}
