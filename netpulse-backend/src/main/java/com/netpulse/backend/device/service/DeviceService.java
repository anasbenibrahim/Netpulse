package com.netpulse.backend.device.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import com.netpulse.backend.anomaly.service.AnomalyService;
import com.netpulse.backend.common.exception.ResourceNotFoundException;
import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.entity.DeviceStatus;
import com.netpulse.backend.device.repository.DeviceRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class DeviceService {

    private static final Logger log = LoggerFactory.getLogger(DeviceService.class);

    private final DeviceRepository deviceRepository;
    private final AnomalyService anomalyService;
    private final Clock clock;

    public DeviceService(DeviceRepository deviceRepository, AnomalyService anomalyService, Clock clock) {
        this.deviceRepository = deviceRepository;
        this.anomalyService = anomalyService;
        this.clock = clock;
    }

    /** Called for every message from a device: registers it if new and marks it ONLINE. */
    public Device recordActivity(String deviceCode) {
        Instant now = clock.instant();
        Device device = findOrRegister(deviceCode, DeviceStatus.ONLINE);
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            device.setStatus(DeviceStatus.ONLINE);
            anomalyService.deviceBackOnline(device);
            log.info("[DEVICE] {} is back online", deviceCode);
        }
        device.setLastSeen(now);
        return device;
    }

    public void updateNetworkIdentity(Device device, String ipAddress, String ssid) {
        if (ipAddress != null && !ipAddress.isBlank()) {
            device.setIpAddress(ipAddress);
        }
        if (ssid != null && !ssid.isBlank()) {
            device.setSsid(ssid);
        }
    }

    public void markOffline(String deviceCode, String reason) {
        markOffline(findOrRegister(deviceCode, DeviceStatus.OFFLINE), reason);
    }

    /** Marks OFFLINE every ONLINE device that has been silent for longer than the timeout. */
    public int markStaleDevicesOffline(Duration timeout) {
        Instant cutoff = clock.instant().minus(timeout);
        List<Device> stale = deviceRepository.findByStatusAndLastSeenBefore(DeviceStatus.ONLINE, cutoff);
        stale.forEach(device -> markOffline(device, "no data for more than " + timeout.toSeconds() + " s"));
        return stale.size();
    }

    @Transactional(readOnly = true)
    public List<Device> findAll() {
        return deviceRepository.findAllByOrderByDeviceCodeAsc();
    }

    @Transactional(readOnly = true)
    public Device getByCode(String deviceCode) {
        return deviceRepository.findByDeviceCode(deviceCode)
                .orElseThrow(() -> new ResourceNotFoundException("Device", deviceCode));
    }

    @Transactional(readOnly = true)
    public long count() {
        return deviceRepository.count();
    }

    @Transactional(readOnly = true)
    public long countByStatus(DeviceStatus status) {
        return deviceRepository.countByStatus(status);
    }

    private void markOffline(Device device, String reason) {
        if (device.getStatus() == DeviceStatus.OFFLINE) {
            return;
        }
        device.setStatus(DeviceStatus.OFFLINE);
        log.warn("[DEVICE] {} is offline: {}", device.getDeviceCode(), reason);
        anomalyService.deviceWentOffline(device, reason);
    }

    private Device findOrRegister(String deviceCode, DeviceStatus initialStatus) {
        return deviceRepository.findByDeviceCode(deviceCode).orElseGet(() -> {
            log.info("[DEVICE] Registering new device {}", deviceCode);
            return deviceRepository.save(Device.register(deviceCode, initialStatus, clock.instant()));
        });
    }
}
