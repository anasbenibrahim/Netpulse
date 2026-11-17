package com.netpulse.backend.alert.entity;

import java.time.Instant;

import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.device.entity.Device;

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

/** Notification for an operator, raised when an anomaly opens or escalates. */
@Entity
@Table(name = "alerts")
@Getter
@NoArgsConstructor
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "anomaly_id", nullable = false)
    private Anomaly anomaly;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 512)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnomalySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "acknowledged_at")
    private Instant acknowledgedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static Alert raise(Anomaly anomaly, String title, Instant now) {
        Alert alert = new Alert();
        alert.device = anomaly.getDevice();
        alert.anomaly = anomaly;
        alert.title = title;
        alert.message = anomaly.getDescription();
        alert.severity = anomaly.getSeverity();
        alert.status = AlertStatus.OPEN;
        alert.createdAt = now;
        return alert;
    }

    public void acknowledge(Instant now) {
        if (status == AlertStatus.OPEN) {
            status = AlertStatus.ACKNOWLEDGED;
            acknowledgedAt = now;
        }
    }

    public void resolve(Instant now) {
        if (status != AlertStatus.RESOLVED) {
            status = AlertStatus.RESOLVED;
            resolvedAt = now;
        }
    }
}
