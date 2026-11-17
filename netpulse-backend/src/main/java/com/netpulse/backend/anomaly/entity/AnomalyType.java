package com.netpulse.backend.anomaly.entity;

/** Every kind of anomaly the rule engine can raise, with the metric it watches. */
public enum AnomalyType {

    WEAK_WIFI_SIGNAL("Weak Wi-Fi signal", "rssi", "dBm", true),
    HIGH_GATEWAY_LATENCY("High gateway latency", "gatewayLatency", "ms", false),
    HIGH_INTERNET_LATENCY("High internet latency", "internetLatency", "ms", false),
    GATEWAY_PACKET_LOSS("Packet loss to gateway", "gatewayPacketLoss", "%", false),
    INTERNET_PACKET_LOSS("Packet loss to internet", "internetPacketLoss", "%", false),
    LOW_MEMORY("Low free heap memory", "freeHeap", "bytes", true),
    DEGRADED_NETWORK_HEALTH("Degraded network health", "networkHealthScore", "/100", true),
    DEVICE_OFFLINE("Device offline", "status", "", false);

    private final String label;
    private final String metricName;
    private final String unit;
    private final boolean lowerIsWorse;

    AnomalyType(String label, String metricName, String unit, boolean lowerIsWorse) {
        this.label = label;
        this.metricName = metricName;
        this.unit = unit;
        this.lowerIsWorse = lowerIsWorse;
    }

    public String label() {
        return label;
    }

    public String metricName() {
        return metricName;
    }

    public String unit() {
        return unit;
    }

    public boolean lowerIsWorse() {
        return lowerIsWorse;
    }
}
