package com.netpulse.backend.telemetry.controller;

import java.time.Duration;
import java.util.List;

import com.netpulse.backend.common.Limits;
import com.netpulse.backend.telemetry.dto.NetworkMetricResponse;
import com.netpulse.backend.telemetry.dto.TelemetryResponse;
import com.netpulse.backend.telemetry.service.TelemetryQueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Time series for the dashboard charts. {@code minutes} is the window, max 7 days. */
@RestController
@RequestMapping("/api/devices/{deviceCode}")
public class TelemetryController {

    private static final int MAX_MINUTES = 7 * 24 * 60;
    private static final int MAX_POINTS = 5000;

    private final TelemetryQueryService queryService;

    public TelemetryController(TelemetryQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/telemetry")
    public List<TelemetryResponse> telemetry(@PathVariable String deviceCode,
                                             @RequestParam(defaultValue = "60") int minutes,
                                             @RequestParam(defaultValue = "1000") int limit) {
        return queryService.telemetryHistory(deviceCode, window(minutes), Limits.clamp(limit, MAX_POINTS));
    }

    @GetMapping("/network")
    public List<NetworkMetricResponse> network(@PathVariable String deviceCode,
                                               @RequestParam(defaultValue = "60") int minutes,
                                               @RequestParam(defaultValue = "1000") int limit) {
        return queryService.networkHistory(deviceCode, window(minutes), Limits.clamp(limit, MAX_POINTS));
    }

    private static Duration window(int minutes) {
        return Duration.ofMinutes(Limits.clamp(minutes, MAX_MINUTES));
    }
}
