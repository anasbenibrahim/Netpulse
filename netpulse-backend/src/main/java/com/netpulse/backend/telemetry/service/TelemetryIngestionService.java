package com.netpulse.backend.telemetry.service;

import java.time.Clock;
import java.time.Instant;

import com.netpulse.backend.anomaly.detector.AnomalyDetector;
import com.netpulse.backend.anomaly.service.AnomalyService;
import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.service.DeviceService;
import com.netpulse.backend.telemetry.dto.NetworkMetricsMessage;
import com.netpulse.backend.telemetry.dto.TelemetryMessage;
import com.netpulse.backend.telemetry.entity.NetworkMetric;
import com.netpulse.backend.telemetry.entity.SystemTelemetry;
import com.netpulse.backend.telemetry.repository.NetworkMetricRepository;
import com.netpulse.backend.telemetry.repository.SystemTelemetryRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** MQTT message -> device update -> saved reading -> anomaly detection. */
@Service
public class TelemetryIngestionService {

    private static final Logger log = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final ObjectMapper objectMapper;
    private final DeviceService deviceService;
    private final SystemTelemetryRepository telemetryRepository;
    private final NetworkMetricRepository networkMetricRepository;
    private final AnomalyDetector detector;
    private final AnomalyService anomalyService;
    private final Clock clock;

    public TelemetryIngestionService(ObjectMapper objectMapper, DeviceService deviceService,
                                     SystemTelemetryRepository telemetryRepository,
                                     NetworkMetricRepository networkMetricRepository,
                                     AnomalyDetector detector, AnomalyService anomalyService, Clock clock) {
        this.objectMapper = objectMapper;
        this.deviceService = deviceService;
        this.telemetryRepository = telemetryRepository;
        this.networkMetricRepository = networkMetricRepository;
        this.detector = detector;
        this.anomalyService = anomalyService;
        this.clock = clock;
    }

    @Transactional
    public void ingestTelemetry(String deviceCode, String payload) {
        TelemetryMessage message = parse(payload, TelemetryMessage.class, deviceCode);
        if (message == null) {
            return;
        }
        Instant now = clock.instant();
        Device device = deviceService.recordActivity(deviceCode);
        deviceService.updateNetworkIdentity(device, message.ip(), message.ssid());
        telemetryRepository.save(SystemTelemetry.from(device, message, now));
        anomalyService.apply(device, detector.evaluate(message));
        log.debug("[TELEMETRY] {} heap={} rssi={}", deviceCode, message.freeHeap(), message.rssi());
    }

    @Transactional
    public void ingestNetworkMetrics(String deviceCode, String payload) {
        NetworkMetricsMessage message = parse(payload, NetworkMetricsMessage.class, deviceCode);
        if (message == null) {
            return;
        }
        Instant now = clock.instant();
        Device device = deviceService.recordActivity(deviceCode);
        networkMetricRepository.save(NetworkMetric.from(device, device.getSsid(), message, now));
        anomalyService.apply(device, detector.evaluate(message));
        log.debug("[NETWORK] {} score={} status={}", deviceCode, message.networkHealthScore(),
                message.networkStatus());
    }

    /**
     * Payload is "online" (sent on connect), "switching" (changing Wi-Fi on request)
     * or "offline" (the device's MQTT last will).
     */
    @Transactional
    public void ingestStatus(String deviceCode, String payload) {
        String status = payload == null ? "" : payload.trim().toLowerCase();
        switch (status) {
            case "online", "switching" -> deviceService.recordActivity(deviceCode);
            case "offline" -> deviceService.markOffline(deviceCode, "connection lost (MQTT last will)");
            default -> log.warn("[STATUS] Unknown status '{}' from {}", payload, deviceCode);
        }
    }

    private <T> T parse(String payload, Class<T> type, String deviceCode) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JacksonException e) {
            log.error("[MQTT] Invalid {} from {}: {} | payload={}", type.getSimpleName(), deviceCode,
                    e.getOriginalMessage(), payload);
            return null;
        }
    }
}
