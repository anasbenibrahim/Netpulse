package com.netpulse.backend.telemetry.dto;

import java.time.Instant;

import com.netpulse.backend.telemetry.entity.NetworkMetric;
import com.netpulse.backend.telemetry.entity.NetworkStatus;

public record NetworkMetricResponse(
        Instant receivedAt,
        String ssid,
        Double rssi,
        Double gatewayLatency,
        Double gatewayPacketLoss,
        Double internetLatency,
        Double internetPacketLoss,
        Double healthScore,
        NetworkStatus networkStatus
) {
    public static NetworkMetricResponse from(NetworkMetric m) {
        return new NetworkMetricResponse(m.getReceivedAt(), m.getSsid(), m.getRssi(), m.getGatewayLatency(),
                m.getGatewayPacketLoss(), m.getInternetLatency(), m.getInternetPacketLoss(),
                m.getHealthScore(), m.getNetworkStatus());
    }
}
