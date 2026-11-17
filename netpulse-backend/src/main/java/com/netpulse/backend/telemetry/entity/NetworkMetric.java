package com.netpulse.backend.telemetry.entity;

import java.time.Instant;

import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.telemetry.dto.NetworkMetricsMessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One reading from netpulse/devices/{code}/network.
 * Latency and packet-loss values are null when the firmware could not measure them.
 */
@Entity
@Table(name = "network_metrics")
@Getter
@NoArgsConstructor
public class NetworkMetric {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    /** Wi-Fi the device was connected to when it took this measurement. */
    @Column(length = 64)
    private String ssid;

    private Double rssi;

    @Column(name = "gateway_latency")
    private Double gatewayLatency;

    @Column(name = "gateway_packet_loss")
    private Double gatewayPacketLoss;

    @Column(name = "internet_latency")
    private Double internetLatency;

    @Column(name = "internet_packet_loss")
    private Double internetPacketLoss;

    @Column(name = "health_score")
    private Double healthScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "network_status", length = 16)
    private NetworkStatus networkStatus;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public static NetworkMetric from(Device device, String ssid, NetworkMetricsMessage message, Instant receivedAt) {
        NetworkMetric m = new NetworkMetric();
        m.device = device;
        m.ssid = ssid;
        m.rssi = message.rssi();
        m.gatewayLatency = message.gatewayLatency();
        m.gatewayPacketLoss = message.gatewayPacketLoss();
        m.internetLatency = message.internetLatency();
        m.internetPacketLoss = message.internetPacketLoss();
        m.healthScore = message.networkHealthScore();
        m.networkStatus = message.networkStatus() == null ? NetworkStatus.UNKNOWN : message.networkStatus();
        m.receivedAt = receivedAt;
        return m;
    }
}
