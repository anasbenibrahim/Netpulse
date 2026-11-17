package com.netpulse.backend.telemetry.dto;

import java.time.Instant;

import com.netpulse.backend.telemetry.entity.SystemTelemetry;

public record TelemetryResponse(
        Instant receivedAt,
        Long uptimeSeconds,
        Long freeHeap,
        Long totalHeap,
        Double heapUsage,
        Double rssi
) {
    public static TelemetryResponse from(SystemTelemetry t) {
        return new TelemetryResponse(t.getReceivedAt(), t.getUptimeSeconds(), t.getFreeHeap(),
                t.getTotalHeap(), t.getHeapUsage(), t.getRssi());
    }
}
