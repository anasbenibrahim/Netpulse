package com.netpulse.backend.telemetry.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.netpulse.backend.telemetry.entity.NetworkStatus;

/**
 * JSON published by the ESP32 on netpulse/devices/{code}/network.
 * Latency and packet-loss fields are null when the firmware could not measure them.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NetworkMetricsMessage(
        String deviceId,
        Double rssi,
        Double gatewayLatency,
        Double gatewayPacketLoss,
        Double internetLatency,
        Double internetPacketLoss,
        Double networkHealthScore,
        NetworkStatus networkStatus
) {
}
