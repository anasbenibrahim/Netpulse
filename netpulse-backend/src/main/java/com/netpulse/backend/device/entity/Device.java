package com.netpulse.backend.device.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** An ESP32, registered automatically the first time it publishes on MQTT. */
@Entity
@Table(name = "devices")
@Getter
@Setter
@NoArgsConstructor
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Identifier taken from the MQTT topic: netpulse/devices/{deviceCode}/... */
    @Column(name = "device_code", nullable = false, unique = true, length = 64)
    private String deviceCode;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(length = 64)
    private String ssid;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DeviceStatus status;

    @Column(name = "last_seen")
    private Instant lastSeen;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public static Device register(String deviceCode, DeviceStatus status, Instant now) {
        Device device = new Device();
        device.deviceCode = deviceCode;
        device.name = deviceCode;
        device.status = status;
        device.createdAt = now;
        return device;
    }
}
