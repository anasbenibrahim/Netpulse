package com.netpulse.backend.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("netpulse.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
