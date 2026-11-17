package com.netpulse.backend.telemetry;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("netpulse.telemetry")
public record TelemetryProperties(Duration retention) {
}
