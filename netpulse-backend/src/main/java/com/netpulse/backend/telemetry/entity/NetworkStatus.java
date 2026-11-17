package com.netpulse.backend.telemetry.entity;

import com.fasterxml.jackson.annotation.JsonCreator;

/** Network quality level computed by the ESP32 firmware (see networkStatus in main.cpp). */
public enum NetworkStatus {
    EXCELLENT,
    GOOD,
    FAIR,
    POOR,
    CRITICAL,
    UNKNOWN;

    /** Unrecognised values become UNKNOWN instead of failing the whole message. */
    @JsonCreator
    public static NetworkStatus fromValue(String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
