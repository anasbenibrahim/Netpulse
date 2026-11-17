package com.netpulse.backend.device.controller;

import java.util.List;

import com.netpulse.backend.anomaly.service.AnomalyService;
import com.netpulse.backend.device.dto.DeviceResponse;
import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.service.DeviceService;
import com.netpulse.backend.telemetry.service.TelemetryQueryService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/devices")
public class DeviceController {

    private final DeviceService deviceService;
    private final AnomalyService anomalyService;
    private final TelemetryQueryService telemetryQueryService;

    public DeviceController(DeviceService deviceService, AnomalyService anomalyService,
                            TelemetryQueryService telemetryQueryService) {
        this.deviceService = deviceService;
        this.anomalyService = anomalyService;
        this.telemetryQueryService = telemetryQueryService;
    }

    @GetMapping
    public List<DeviceResponse> list() {
        return deviceService.findAll().stream().map(this::toResponse).toList();
    }

    @GetMapping("/{deviceCode}")
    public DeviceResponse get(@PathVariable String deviceCode) {
        return toResponse(deviceService.getByCode(deviceCode));
    }

    private DeviceResponse toResponse(Device device) {
        return DeviceResponse.from(device, anomalyService.countActive(device),
                telemetryQueryService.latestNetwork(device).orElse(null));
    }
}
