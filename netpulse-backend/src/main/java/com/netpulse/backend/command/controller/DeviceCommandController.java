package com.netpulse.backend.command.controller;

import java.util.List;

import com.netpulse.backend.command.dto.CommandResponse;
import com.netpulse.backend.command.dto.WifiConnectRequest;
import com.netpulse.backend.command.service.DeviceCommandService;
import com.netpulse.backend.common.Limits;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Commands are asynchronous: POST returns 202 with the command, then the dashboard
 * polls GET /api/commands/{id} until its status is no longer SENT.
 */
@RestController
@RequestMapping("/api")
public class DeviceCommandController {

    private final DeviceCommandService commandService;

    public DeviceCommandController(DeviceCommandService commandService) {
        this.commandService = commandService;
    }

    @PostMapping("/devices/{deviceCode}/wifi/scan")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandResponse scan(@PathVariable String deviceCode) {
        return commandService.requestWifiScan(deviceCode);
    }

    @PostMapping("/devices/{deviceCode}/wifi/connect")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public CommandResponse connect(@PathVariable String deviceCode, @Valid @RequestBody WifiConnectRequest request) {
        return commandService.requestWifiConnect(deviceCode, request);
    }

    @GetMapping("/devices/{deviceCode}/commands")
    public List<CommandResponse> history(@PathVariable String deviceCode,
                                         @RequestParam(defaultValue = "10") int limit) {
        return commandService.history(deviceCode, Limits.clamp(limit, 100));
    }

    @GetMapping("/commands/{id}")
    public CommandResponse get(@PathVariable String id) {
        return commandService.get(id);
    }
}
