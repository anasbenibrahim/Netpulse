package com.netpulse.backend.anomaly.entity;

import java.time.Instant;

import com.netpulse.backend.anomaly.detector.Evaluation;
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

/**
 * An anomaly "episode": opened when a metric crosses its threshold, updated while
 * it stays abnormal, and resolved once it has been normal for a few readings.
 */
@Entity
@Table(name = "anomalies")
@Getter
@NoArgsConstructor
public class Anomaly {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private AnomalyType type;

    @Column(name = "metric_name", nullable = false, length = 40)
    private String metricName;

    /** Highest severity reached during the episode. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnomalySeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AnomalyStatus status;

    /** Latest abnormal value. */
    @Column(name = "detected_value")
    private Double detectedValue;

    @Column(name = "threshold_value")
    private Double thresholdValue;

    @Column(nullable = false, length = 512)
    private String description;

    /** Number of abnormal readings in this episode. */
    @Column(nullable = false)
    private int occurrences;

    /** Consecutive normal readings since the last abnormal one. */
    @Column(name = "normal_streak", nullable = false)
    private int normalStreak;

    @Column(name = "first_detected_at", nullable = false)
    private Instant firstDetectedAt;

    @Column(name = "last_detected_at", nullable = false)
    private Instant lastDetectedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    public static Anomaly open(Device device, Evaluation evaluation, Instant now) {
        Anomaly a = new Anomaly();
        a.device = device;
        a.type = evaluation.type();
        a.metricName = evaluation.type().metricName();
        a.severity = evaluation.severity();
        a.status = AnomalyStatus.ACTIVE;
        a.detectedValue = evaluation.value();
        a.thresholdValue = evaluation.threshold();
        a.description = evaluation.description();
        a.occurrences = 1;
        a.normalStreak = 0;
        a.firstDetectedAt = now;
        a.lastDetectedAt = now;
        return a;
    }

    /** Records another abnormal reading. Returns true if the severity was escalated. */
    public boolean recordOccurrence(Evaluation evaluation, Instant now) {
        occurrences++;
        normalStreak = 0;
        lastDetectedAt = now;
        detectedValue = evaluation.value();
        description = evaluation.description();
        if (severity == AnomalySeverity.WARNING && evaluation.severity() == AnomalySeverity.CRITICAL) {
            severity = AnomalySeverity.CRITICAL;
            thresholdValue = evaluation.threshold();
            return true;
        }
        return false;
    }

    /** Records a normal reading and returns the current streak of normal readings. */
    public int recordNormalReading() {
        return ++normalStreak;
    }

    public void resolve(Instant now) {
        status = AnomalyStatus.RESOLVED;
        resolvedAt = now;
    }
}
