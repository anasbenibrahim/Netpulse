package com.netpulse.backend.telemetry.entity;

import java.time.Instant;

import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.telemetry.dto.TelemetryMessage;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One reading from netpulse/devices/{code}/telemetry (ESP32 system health). */
@Entity
@Table(name = "telemetry")
@Getter
@NoArgsConstructor
public class SystemTelemetry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Column(name = "uptime_seconds")
    private Long uptimeSeconds;

    @Column(name = "free_heap")
    private Long freeHeap;

    @Column(name = "total_heap")
    private Long totalHeap;

    @Column(name = "heap_usage")
    private Double heapUsage;

    @Column(name = "flash_size_mb")
    private Integer flashSizeMb;

    @Column(length = 64)
    private String ssid;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    private Double rssi;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    public static SystemTelemetry from(Device device, TelemetryMessage message, Instant receivedAt) {
        SystemTelemetry t = new SystemTelemetry();
        t.device = device;
        t.uptimeSeconds = message.uptime();
        t.freeHeap = message.freeHeap();
        t.totalHeap = message.totalHeap();
        t.heapUsage = message.heapUsage();
        t.flashSizeMb = message.flashSizeMB();
        t.ssid = message.ssid();
        t.ipAddress = message.ip();
        t.rssi = message.rssi();
        t.receivedAt = receivedAt;
        return t;
    }
}
