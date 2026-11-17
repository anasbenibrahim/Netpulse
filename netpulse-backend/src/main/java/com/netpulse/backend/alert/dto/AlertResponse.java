package com.netpulse.backend.alert.dto;

import java.time.Instant;

import com.netpulse.backend.alert.entity.Alert;
import com.netpulse.backend.alert.entity.AlertStatus;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;

public record AlertResponse(
        Long id,
        String deviceCode,
        Long anomalyId,
        String title,
        String message,
        AnomalySeverity severity,
        AlertStatus status,
        Instant createdAt,
        Instant acknowledgedAt,
        Instant resolvedAt
) {
    public static AlertResponse from(Alert a) {
        return new AlertResponse(a.getId(), a.getDevice().getDeviceCode(), a.getAnomaly().getId(),
                a.getTitle(), a.getMessage(), a.getSeverity(), a.getStatus(), a.getCreatedAt(),
                a.getAcknowledgedAt(), a.getResolvedAt());
    }
}
