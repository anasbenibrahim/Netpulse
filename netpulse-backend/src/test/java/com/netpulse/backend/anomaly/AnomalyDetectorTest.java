package com.netpulse.backend.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.netpulse.backend.anomaly.detector.AnomalyDetector;
import com.netpulse.backend.anomaly.detector.AnomalyProperties;
import com.netpulse.backend.anomaly.detector.AnomalyProperties.Threshold;
import com.netpulse.backend.anomaly.detector.Evaluation;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyType;
import com.netpulse.backend.telemetry.dto.NetworkMetricsMessage;
import com.netpulse.backend.telemetry.dto.TelemetryMessage;
import com.netpulse.backend.telemetry.entity.NetworkStatus;

import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

    private final AnomalyDetector detector = new AnomalyDetector(new AnomalyProperties(
            new Threshold(-80, -90),
            new Threshold(20000, 10000),
            new Threshold(60, 40),
            new Threshold(100, 300),
            new Threshold(300, 1000),
            new Threshold(10, 50),
            3));

    @Test
    void healthyNetworkProducesOnlyNormalEvaluations() {
        List<Evaluation> results = detector.evaluate(network(-55.0, 5.0, 0.0, 40.0, 0.0, 95.0));

        assertThat(results).hasSize(6).noneMatch(Evaluation::isViolation);
    }

    @Test
    void weakSignalIsWarningThenCritical() {
        assertThat(severityOf(AnomalyType.WEAK_WIFI_SIGNAL, network(-85.0, 5.0, 0.0, 40.0, 0.0, 95.0)))
                .isEqualTo(AnomalySeverity.WARNING);
        assertThat(severityOf(AnomalyType.WEAK_WIFI_SIGNAL, network(-95.0, 5.0, 0.0, 40.0, 0.0, 95.0)))
                .isEqualTo(AnomalySeverity.CRITICAL);
    }

    @Test
    void highLatencyAndPacketLossAreDetected() {
        NetworkMetricsMessage message = network(-60.0, 350.0, 60.0, 1200.0, 100.0, 20.0);

        assertThat(severityOf(AnomalyType.HIGH_GATEWAY_LATENCY, message)).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(severityOf(AnomalyType.GATEWAY_PACKET_LOSS, message)).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(severityOf(AnomalyType.HIGH_INTERNET_LATENCY, message)).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(severityOf(AnomalyType.INTERNET_PACKET_LOSS, message)).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(severityOf(AnomalyType.DEGRADED_NETWORK_HEALTH, message)).isEqualTo(AnomalySeverity.CRITICAL);
    }

    @Test
    void unmeasuredMetricsAreSkipped() {
        List<Evaluation> results = detector.evaluate(network(-60.0, null, null, null, null, 80.0));

        assertThat(results).extracting(Evaluation::type)
                .containsExactly(AnomalyType.WEAK_WIFI_SIGNAL, AnomalyType.DEGRADED_NETWORK_HEALTH);
    }

    @Test
    void lowHeapIsDetected() {
        TelemetryMessage telemetry = new TelemetryMessage("esp32-01", 10L, 8000L, 300000L, 97.3, 4, "home",
                "192.168.1.50", -60.0);

        Evaluation evaluation = detector.evaluate(telemetry).getFirst();

        assertThat(evaluation.type()).isEqualTo(AnomalyType.LOW_MEMORY);
        assertThat(evaluation.severity()).isEqualTo(AnomalySeverity.CRITICAL);
        assertThat(evaluation.description()).contains("8000 bytes");
    }

    private AnomalySeverity severityOf(AnomalyType type, NetworkMetricsMessage message) {
        return detector.evaluate(message).stream()
                .filter(e -> e.type() == type)
                .findFirst()
                .orElseThrow()
                .severity();
    }

    private static NetworkMetricsMessage network(Double rssi, Double gwLatency, Double gwLoss,
                                                 Double netLatency, Double netLoss, Double score) {
        return new NetworkMetricsMessage("esp32-01", rssi, gwLatency, gwLoss, netLatency, netLoss, score,
                NetworkStatus.GOOD);
    }
}
