package com.netpulse.backend.device;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;

import com.netpulse.backend.device.service.DeviceService;
import com.netpulse.backend.device.service.DeviceStatusMonitor;
import com.netpulse.backend.support.MutableClock;

import org.junit.jupiter.api.Test;

class DeviceStatusMonitorTest {

    private static final Duration TIMEOUT = Duration.ofSeconds(30);

    private final MutableClock clock = new MutableClock(Instant.parse("2026-11-17T10:00:00Z"));
    private final DeviceService deviceService = mock(DeviceService.class);
    private final DeviceStatusMonitor monitor =
            new DeviceStatusMonitor(deviceService, new DeviceProperties(TIMEOUT, 10_000), clock);

    @Test
    void doesNotMarkDevicesOfflineRightAfterBackendRestart() {
        clock.advance(Duration.ofSeconds(10));

        monitor.checkForSilentDevices();

        verify(deviceService, never()).markStaleDevicesOffline(TIMEOUT);
    }

    @Test
    void checksSilentDevicesOnceDevicesHadTimeToReport() {
        clock.advance(TIMEOUT.plusSeconds(1));

        monitor.checkForSilentDevices();

        verify(deviceService).markStaleDevicesOffline(TIMEOUT);
    }
}
