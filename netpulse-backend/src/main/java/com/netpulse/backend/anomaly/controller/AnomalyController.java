package com.netpulse.backend.anomaly.controller;

import java.util.List;

import com.netpulse.backend.anomaly.dto.AnomalyResponse;
import com.netpulse.backend.anomaly.entity.AnomalyStatus;
import com.netpulse.backend.anomaly.service.AnomalyService;
import com.netpulse.backend.common.Limits;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/anomalies")
public class AnomalyController {

    private final AnomalyService anomalyService;

    public AnomalyController(AnomalyService anomalyService) {
        this.anomalyService = anomalyService;
    }

    /** e.g. /api/anomalies?status=ACTIVE&device=esp32-01 */
    @GetMapping
    public List<AnomalyResponse> list(@RequestParam(required = false) AnomalyStatus status,
                                      @RequestParam(required = false) String device,
                                      @RequestParam(defaultValue = "100") int limit) {
        return anomalyService.list(status, device, Limits.clamp(limit, 500));
    }
}
