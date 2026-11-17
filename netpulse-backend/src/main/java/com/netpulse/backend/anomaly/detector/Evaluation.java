package com.netpulse.backend.anomaly.detector;

import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyType;

/**
 * Result of checking one metric against its rule.
 * severity == null means the value is normal.
 */
public record Evaluation(
        AnomalyType type,
        AnomalySeverity severity,
        Double value,
        Double threshold,
        String description
) {
    public boolean isViolation() {
        return severity != null;
    }
}
