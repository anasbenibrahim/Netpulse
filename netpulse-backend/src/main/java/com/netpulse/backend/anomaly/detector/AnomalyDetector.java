package com.netpulse.backend.anomaly.detector;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.netpulse.backend.anomaly.detector.AnomalyProperties.Threshold;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyType;
import com.netpulse.backend.telemetry.dto.NetworkMetricsMessage;
import com.netpulse.backend.telemetry.dto.TelemetryMessage;

import org.springframework.stereotype.Component;

/**
 * Rule-based detection: each metric is compared with a warning and a critical threshold.
 * Metrics the firmware could not measure (null) are skipped, so they neither open
 * nor resolve an anomaly.
 */
@Component
public class AnomalyDetector {

    private final AnomalyProperties properties;

    public AnomalyDetector(AnomalyProperties properties) {
        this.properties = properties;
    }

    public List<Evaluation> evaluate(TelemetryMessage message) {
        List<Evaluation> results = new ArrayList<>();
        Double freeHeap = message.freeHeap() == null ? null : message.freeHeap().doubleValue();
        addIfMeasured(results, check(AnomalyType.LOW_MEMORY, freeHeap, properties.freeHeap()));
        return results;
    }

    public List<Evaluation> evaluate(NetworkMetricsMessage message) {
        List<Evaluation> results = new ArrayList<>();
        addIfMeasured(results, check(AnomalyType.WEAK_WIFI_SIGNAL, message.rssi(), properties.rssi()));
        addIfMeasured(results, check(AnomalyType.HIGH_GATEWAY_LATENCY, message.gatewayLatency(), properties.gatewayLatency()));
        addIfMeasured(results, check(AnomalyType.HIGH_INTERNET_LATENCY, message.internetLatency(), properties.internetLatency()));
        addIfMeasured(results, check(AnomalyType.GATEWAY_PACKET_LOSS, message.gatewayPacketLoss(), properties.packetLoss()));
        addIfMeasured(results, check(AnomalyType.INTERNET_PACKET_LOSS, message.internetPacketLoss(), properties.packetLoss()));
        addIfMeasured(results, check(AnomalyType.DEGRADED_NETWORK_HEALTH, message.networkHealthScore(), properties.healthScore()));
        return results;
    }

    public Evaluation deviceOffline(String reason) {
        return new Evaluation(AnomalyType.DEVICE_OFFLINE, AnomalySeverity.CRITICAL, null, null,
                AnomalyType.DEVICE_OFFLINE.label() + ": " + reason);
    }

    static Evaluation check(AnomalyType type, Double value, Threshold threshold) {
        if (value == null) {
            return null;
        }
        if (isWorse(type, value, threshold.critical())) {
            return violation(type, AnomalySeverity.CRITICAL, value, threshold.critical());
        }
        if (isWorse(type, value, threshold.warning())) {
            return violation(type, AnomalySeverity.WARNING, value, threshold.warning());
        }
        return new Evaluation(type, null, value, threshold.warning(), type.label() + ": back to normal");
    }

    private static boolean isWorse(AnomalyType type, double value, double limit) {
        return type.lowerIsWorse() ? value < limit : value > limit;
    }

    private static Evaluation violation(AnomalyType type, AnomalySeverity severity, double value, double limit) {
        String description = String.format(Locale.ROOT, "%s: %s %s (%s threshold %s %s)",
                type.label(), format(value), type.unit(), severity.name().toLowerCase(Locale.ROOT),
                format(limit), type.unit()).replace("  ", " ");
        return new Evaluation(type, severity, value, limit, description);
    }

    private static String format(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private static void addIfMeasured(List<Evaluation> results, Evaluation evaluation) {
        if (evaluation != null) {
            results.add(evaluation);
        }
    }
}
