package com.netpulse.backend.command.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** JSON published by the device on netpulse/devices/{code}/command-result. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CommandResultMessage(
        String requestId,
        String action,
        String status,
        String message,
        String ssid,
        String ip,
        List<WifiNetwork> networks
) {
    public boolean succeeded() {
        return "SUCCEEDED".equalsIgnoreCase(status);
    }
}
