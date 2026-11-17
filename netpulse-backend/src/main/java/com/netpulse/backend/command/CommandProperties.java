package com.netpulse.backend.command;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("netpulse.command")
public record CommandProperties(Duration timeout) {
}
