package com.netpulse.backend.command.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** One network seen by the device's Wi-Fi scan. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record WifiNetwork(String ssid, Integer rssi, Integer channel, String security) {
}
