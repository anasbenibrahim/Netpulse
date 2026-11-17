package com.netpulse.backend.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** JSON published by the ESP32 on netpulse/devices/{code}/telemetry. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelemetryMessage(
        String deviceId,
        Long uptime,
        Long freeHeap,
        Long totalHeap,
        Double heapUsage,
        Integer flashSizeMB,
        String ssid,
        String ip,
        Double rssi
) {
}
