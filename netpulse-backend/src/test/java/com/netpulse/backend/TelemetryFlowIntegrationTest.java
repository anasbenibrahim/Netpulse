package com.netpulse.backend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.netpulse.backend.alert.entity.Alert;
import com.netpulse.backend.alert.entity.AlertStatus;
import com.netpulse.backend.alert.repository.AlertRepository;
import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyStatus;
import com.netpulse.backend.anomaly.entity.AnomalyType;
import com.netpulse.backend.anomaly.repository.AnomalyRepository;
import com.netpulse.backend.device.DeviceProperties;
import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.entity.DeviceStatus;
import com.netpulse.backend.device.repository.DeviceRepository;
import com.netpulse.backend.device.service.DeviceService;
import com.netpulse.backend.support.MutableClock;
import com.netpulse.backend.telemetry.repository.NetworkMetricRepository;
import com.netpulse.backend.telemetry.repository.SystemTelemetryRepository;
import com.netpulse.backend.telemetry.service.TelemetryIngestionService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Simulates the ESP32 (same JSON as firmware/esp32/src/main.cpp) and checks the whole
 * chain: MQTT payload -> database -> anomaly -> alert -> REST API. Runs on H2, no broker.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TelemetryFlowIntegrationTest {

    private static final String DEVICE = "esp32-01";
    private static final Instant START = Instant.parse("2026-11-17T10:00:00Z");

    @TestConfiguration
    static class TestClockConfig {
        @Bean
        @Primary
        MutableClock testClock() {
            return new MutableClock(START);
        }
    }

    @Autowired private TelemetryIngestionService ingestion;
    @Autowired private DeviceService deviceService;
    @Autowired private DeviceProperties deviceProperties;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private SystemTelemetryRepository telemetryRepository;
    @Autowired private NetworkMetricRepository networkRepository;
    @Autowired private AnomalyRepository anomalyRepository;
    @Autowired private AlertRepository alertRepository;
    @Autowired private MutableClock clock;
    @Autowired private MockMvc mockMvc;

    @BeforeEach
    void resetClock() {
        clock.set(START);
    }

    @Test
    void telemetryRegistersDeviceAndIsStored() {
        ingestion.ingestTelemetry(DEVICE, telemetryJson(200000));

        Device device = deviceRepository.findByDeviceCode(DEVICE).orElseThrow();
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(device.getIpAddress()).isEqualTo("192.168.1.50");
        assertThat(device.getLastSeen()).isEqualTo(START);
        assertThat(telemetryRepository.count()).isEqualTo(1);
        assertThat(anomalyRepository.count()).isZero();
    }

    @Test
    void unavailableMeasurementsAreStoredAsNull() {
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-60.00", "null", "null", "GOOD"));

        var metric = networkRepository.findAll().getFirst();
        assertThat(metric.getGatewayLatency()).isNull();
        assertThat(metric.getInternetLatency()).isEqualTo(25.5);
        assertThat(anomalyRepository.count()).isZero();
    }

    @Test
    void malformedPayloadIsIgnored() {
        ingestion.ingestTelemetry(DEVICE, "not json");

        assertThat(deviceRepository.count()).isZero();
    }

    @Test
    void weakSignalOpensEscalatesAndResolvesAnAnomaly() {
        // Opens a WARNING anomaly with one alert
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-85.00", "5.00", "0.00", "FAIR"));
        Anomaly anomaly = activeAnomaly(AnomalyType.WEAK_WIFI_SIGNAL);
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.WARNING);
        assertThat(alertRepository.count()).isEqualTo(1);

        // Same episode, escalated to CRITICAL: a second alert, still one anomaly
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-95.00", "5.00", "0.00", "POOR"));
        assertThat(anomaly.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(anomaly.getOccurrences()).isEqualTo(2);
        assertThat(anomalyRepository.count()).isEqualTo(1);
        assertThat(alertRepository.count()).isEqualTo(2);

        // Two normal readings are not enough (resolve-after=3)
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-55.00", "5.00", "0.00", "GOOD"));
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-55.00", "5.00", "0.00", "GOOD"));
        assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.ACTIVE);

        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-55.00", "5.00", "0.00", "GOOD"));
        assertThat(anomaly.getStatus()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(alertRepository.findAll()).extracting(Alert::getStatus).containsOnly(AlertStatus.RESOLVED);
    }

    @Test
    void silentDeviceGoesOfflineAndComesBack() {
        ingestion.ingestTelemetry(DEVICE, telemetryJson(200000));

        clock.advance(deviceProperties.offlineTimeout().plusSeconds(1));
        assertThat(deviceService.markStaleDevicesOffline(deviceProperties.offlineTimeout())).isEqualTo(1);

        Device device = deviceRepository.findByDeviceCode(DEVICE).orElseThrow();
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.OFFLINE);
        Anomaly offline = activeAnomaly(AnomalyType.DEVICE_OFFLINE);
        assertThat(offline.getSeverity()).isEqualTo(AnomalySeverity.CRITICAL);

        ingestion.ingestStatus(DEVICE, "online");
        assertThat(device.getStatus()).isEqualTo(DeviceStatus.ONLINE);
        assertThat(offline.getStatus()).isEqualTo(AnomalyStatus.RESOLVED);
    }

    @Test
    void lastWillMarksDeviceOffline() {
        ingestion.ingestTelemetry(DEVICE, telemetryJson(200000));
        ingestion.ingestStatus(DEVICE, "offline");

        assertThat(deviceRepository.findByDeviceCode(DEVICE).orElseThrow().getStatus())
                .isEqualTo(DeviceStatus.OFFLINE);
        assertThat(activeAnomaly(AnomalyType.DEVICE_OFFLINE).getDescription()).contains("last will");
    }

    @Test
    void restApiExposesDevicesMetricsAnomaliesAndAlerts() throws Exception {
        ingestion.ingestTelemetry(DEVICE, telemetryJson(200000));
        clock.advance(Duration.ofSeconds(5));
        ingestion.ingestNetworkMetrics(DEVICE, networkJson("-85.00", "5.00", "0.00", "FAIR"));

        mockMvc.perform(get("/api/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalDevices").value(1))
                .andExpect(jsonPath("$.onlineDevices").value(1))
                .andExpect(jsonPath("$.activeAnomalies").value(1))
                .andExpect(jsonPath("$.unresolvedAlerts").value(1))
                .andExpect(jsonPath("$.recentAlerts[0].deviceCode").value(DEVICE));

        mockMvc.perform(get("/api/devices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].deviceCode").value(DEVICE))
                .andExpect(jsonPath("$[0].activeAnomalies").value(1))
                .andExpect(jsonPath("$[0].latestNetwork.rssi").value(-85.0))
                .andExpect(jsonPath("$[0].latestNetwork.networkStatus").value("FAIR"));

        mockMvc.perform(get("/api/devices/{code}/telemetry", DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].freeHeap").value(200000));

        mockMvc.perform(get("/api/devices/{code}/network", DEVICE).param("minutes", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].receivedAt").value("2026-11-17T10:00:05Z"));

        mockMvc.perform(get("/api/anomalies").param("status", "ACTIVE").param("device", DEVICE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].type").value("WEAK_WIFI_SIGNAL"))
                .andExpect(jsonPath("$[0].severity").value("WARNING"));

        List<Alert> alerts = alertRepository.findAll();
        mockMvc.perform(post("/api/alerts/{id}/acknowledge", alerts.getFirst().getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }

    @Test
    void restApiReportsErrorsAsProblemDetails() throws Exception {
        mockMvc.perform(get("/api/devices/{code}", "unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Device not found: unknown"));

        mockMvc.perform(get("/api/alerts").param("status", "FOO"))
                .andExpect(status().isBadRequest());
    }

    private Anomaly activeAnomaly(AnomalyType type) {
        Device device = deviceRepository.findByDeviceCode(DEVICE).orElseThrow();
        return anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(device.getId(), type, AnomalyStatus.ACTIVE)
                .orElseThrow(() -> new AssertionError("No active " + type));
    }

    private static String telemetryJson(long freeHeap) {
        return """
                {"deviceId":"esp32-01","uptime":120,"freeHeap":%d,"totalHeap":300000,"heapUsage":33.33,
                 "flashSizeMB":4,"ssid":"home","ip":"192.168.1.50","rssi":-61.00}""".formatted(freeHeap);
    }

    private static String networkJson(String rssi, String gatewayLatency, String gatewayLoss, String status) {
        return """
                {"deviceId":"esp32-01","rssi":%s,"gatewayLatency":%s,"gatewayPacketLoss":%s,
                 "internetLatency":25.50,"internetPacketLoss":0.00,"networkHealthScore":80.00,
                 "networkStatus":"%s"}""".formatted(rssi, gatewayLatency, gatewayLoss, status);
    }
}
