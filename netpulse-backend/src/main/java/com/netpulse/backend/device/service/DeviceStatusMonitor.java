package com.netpulse.backend.device.service;

import java.time.Clock;
import java.time.Instant;

import com.netpulse.backend.device.DeviceProperties;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Detects devices that stopped publishing without sending their MQTT last will. */
@Component
public class DeviceStatusMonitor {

    private final DeviceService deviceService;
    private final DeviceProperties properties;
    private final Clock clock;
    private final Instant startedAt;

    public DeviceStatusMonitor(DeviceService deviceService, DeviceProperties properties, Clock clock) {
        this.deviceService = deviceService;
        this.properties = properties;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    @Scheduled(fixedDelayString = "${netpulse.device.offline-check-interval-ms}")
    public void checkForSilentDevices() {
        // Right after a backend restart every device looks silent (its last_seen dates from
        // before the downtime). Give devices one full timeout to publish before judging them.
        if (clock.instant().isBefore(startedAt.plus(properties.offlineTimeout()))) {
            return;
        }
        deviceService.markStaleDevicesOffline(properties.offlineTimeout());
    }
}
