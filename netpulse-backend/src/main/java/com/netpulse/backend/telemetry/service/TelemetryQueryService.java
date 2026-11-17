package com.netpulse.backend.telemetry.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.service.DeviceService;
import com.netpulse.backend.telemetry.dto.NetworkMetricResponse;
import com.netpulse.backend.telemetry.dto.TelemetryResponse;
import com.netpulse.backend.telemetry.repository.NetworkMetricRepository;
import com.netpulse.backend.telemetry.repository.SystemTelemetryRepository;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class TelemetryQueryService {

    private final DeviceService deviceService;
    private final SystemTelemetryRepository telemetryRepository;
    private final NetworkMetricRepository networkMetricRepository;
    private final Clock clock;

    public TelemetryQueryService(DeviceService deviceService, SystemTelemetryRepository telemetryRepository,
                                 NetworkMetricRepository networkMetricRepository, Clock clock) {
        this.deviceService = deviceService;
        this.telemetryRepository = telemetryRepository;
        this.networkMetricRepository = networkMetricRepository;
        this.clock = clock;
    }

    /** Readings of the last {@code window}, oldest first (ready for a chart). */
    public List<TelemetryResponse> telemetryHistory(String deviceCode, Duration window, int limit) {
        Device device = deviceService.getByCode(deviceCode);
        Instant from = clock.instant().minus(window);
        return telemetryRepository
                .findByDeviceIdAndReceivedAtAfterOrderByReceivedAtDesc(device.getId(), from, Limit.of(limit))
                .reversed().stream()
                .map(TelemetryResponse::from)
                .toList();
    }

    public List<NetworkMetricResponse> networkHistory(String deviceCode, Duration window, int limit) {
        Device device = deviceService.getByCode(deviceCode);
        Instant from = clock.instant().minus(window);
        return networkMetricRepository
                .findByDeviceIdAndReceivedAtAfterOrderByReceivedAtDesc(device.getId(), from, Limit.of(limit))
                .reversed().stream()
                .map(NetworkMetricResponse::from)
                .toList();
    }

    public Optional<NetworkMetricResponse> latestNetwork(Device device) {
        return networkMetricRepository.findFirstByDeviceIdOrderByReceivedAtDesc(device.getId())
                .map(NetworkMetricResponse::from);
    }
}
