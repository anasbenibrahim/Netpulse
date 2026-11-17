package com.netpulse.backend.device.dto;

import java.time.Instant;

import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.entity.DeviceStatus;
import com.netpulse.backend.telemetry.dto.NetworkMetricResponse;

/** A device plus its latest network reading, so the dashboard needs one call. */
public record DeviceResponse(
        Long id,
        String deviceCode,
        String name,
        DeviceStatus status,
        String ipAddress,
        String ssid,
        Instant lastSeen,
        Instant createdAt,
        long activeAnomalies,
        NetworkMetricResponse latestNetwork
) {
    public static DeviceResponse from(Device d, long activeAnomalies, NetworkMetricResponse latestNetwork) {
        return new DeviceResponse(d.getId(), d.getDeviceCode(), d.getName(), d.getStatus(), d.getIpAddress(),
                d.getSsid(), d.getLastSeen(), d.getCreatedAt(), activeAnomalies, latestNetwork);
    }
}
