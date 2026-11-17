package com.netpulse.backend.anomaly.dto;

import java.time.Instant;

import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyStatus;
import com.netpulse.backend.anomaly.entity.AnomalyType;

public record AnomalyResponse(
        Long id,
        String deviceCode,
        AnomalyType type,
        String label,
        String metricName,
        String unit,
        AnomalySeverity severity,
        AnomalyStatus status,
        Double detectedValue,
        Double thresholdValue,
        String description,
        int occurrences,
        Instant firstDetectedAt,
        Instant lastDetectedAt,
        Instant resolvedAt
) {
    public static AnomalyResponse from(Anomaly a) {
        return new AnomalyResponse(a.getId(), a.getDevice().getDeviceCode(), a.getType(), a.getType().label(),
                a.getMetricName(), a.getType().unit(), a.getSeverity(), a.getStatus(), a.getDetectedValue(),
                a.getThresholdValue(), a.getDescription(), a.getOccurrences(), a.getFirstDetectedAt(),
                a.getLastDetectedAt(), a.getResolvedAt());
    }
}
