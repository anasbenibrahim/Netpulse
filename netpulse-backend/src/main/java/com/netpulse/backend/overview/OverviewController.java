package com.netpulse.backend.overview;

import com.netpulse.backend.alert.service.AlertService;
import com.netpulse.backend.anomaly.service.AnomalyService;
import com.netpulse.backend.device.entity.DeviceStatus;
import com.netpulse.backend.device.service.DeviceService;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Headline numbers for the dashboard's Overview page. */
@RestController
@RequestMapping("/api/overview")
public class OverviewController {

    private static final int RECENT_ALERTS = 5;

    private final DeviceService deviceService;
    private final AnomalyService anomalyService;
    private final AlertService alertService;

    public OverviewController(DeviceService deviceService, AnomalyService anomalyService,
                              AlertService alertService) {
        this.deviceService = deviceService;
        this.anomalyService = anomalyService;
        this.alertService = alertService;
    }

    @GetMapping
    public OverviewResponse overview() {
        return new OverviewResponse(
                deviceService.count(),
                deviceService.countByStatus(DeviceStatus.ONLINE),
                deviceService.countByStatus(DeviceStatus.OFFLINE),
                anomalyService.countActive(),
                anomalyService.countActiveCritical(),
                alertService.countUnresolved(),
                alertService.list(null, RECENT_ALERTS));
    }
}
