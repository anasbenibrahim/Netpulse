package com.netpulse.backend.anomaly.detector;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Thresholds from netpulse.anomaly.* in application.properties. */
@ConfigurationProperties("netpulse.anomaly")
public record AnomalyProperties(
        Threshold rssi,
        Threshold freeHeap,
        Threshold healthScore,
        Threshold gatewayLatency,
        Threshold internetLatency,
        Threshold packetLoss,
        int resolveAfter
) {
    public record Threshold(double warning, double critical) {
    }
}
