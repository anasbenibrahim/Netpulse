package com.netpulse.backend.alert.controller;

import java.util.List;

import com.netpulse.backend.alert.dto.AlertResponse;
import com.netpulse.backend.alert.entity.AlertStatus;
import com.netpulse.backend.alert.service.AlertService;
import com.netpulse.backend.common.Limits;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    private final AlertService alertService;

    public AlertController(AlertService alertService) {
        this.alertService = alertService;
    }

    @GetMapping
    public List<AlertResponse> list(@RequestParam(required = false) AlertStatus status,
                                    @RequestParam(defaultValue = "100") int limit) {
        return alertService.list(status, Limits.clamp(limit, 500));
    }

    @PostMapping("/{id}/acknowledge")
    public AlertResponse acknowledge(@PathVariable Long id) {
        return alertService.acknowledge(id);
    }

    @PostMapping("/{id}/resolve")
    public AlertResponse resolve(@PathVariable Long id) {
        return alertService.resolve(id);
    }
}
