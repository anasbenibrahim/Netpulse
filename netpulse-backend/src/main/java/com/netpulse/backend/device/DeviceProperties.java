package com.netpulse.backend.device;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("netpulse.device")
public record DeviceProperties(Duration offlineTimeout, long offlineCheckIntervalMs) {
}
