package com.netpulse.backend.telemetry.service;

import java.time.Clock;
import java.time.Instant;

import com.netpulse.backend.telemetry.TelemetryProperties;
import com.netpulse.backend.telemetry.repository.NetworkMetricRepository;
import com.netpulse.backend.telemetry.repository.SystemTelemetryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** At ~2 readings per 5 s per device, raw telemetry grows fast; old rows are purged nightly. */
@Component
public class TelemetryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(TelemetryRetentionJob.class);

    private final SystemTelemetryRepository telemetryRepository;
    private final NetworkMetricRepository networkMetricRepository;
    private final TelemetryProperties properties;
    private final Clock clock;

    public TelemetryRetentionJob(SystemTelemetryRepository telemetryRepository,
                                 NetworkMetricRepository networkMetricRepository,
                                 TelemetryProperties properties, Clock clock) {
        this.telemetryRepository = telemetryRepository;
        this.networkMetricRepository = networkMetricRepository;
        this.properties = properties;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 3 * * *")
    @Transactional
    public void purgeOldReadings() {
        Instant cutoff = clock.instant().minus(properties.retention());
        int telemetry = telemetryRepository.deleteOlderThan(cutoff);
        int network = networkMetricRepository.deleteOlderThan(cutoff);
        log.info("[RETENTION] Deleted {} telemetry and {} network rows older than {}", telemetry, network, cutoff);
    }
}
