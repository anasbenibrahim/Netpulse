package com.netpulse.backend.command.entity;

/** {@code deviceAction} is the "action" field the firmware expects in the MQTT command. */
public enum CommandType {
    WIFI_SCAN("scan"),
    WIFI_CONNECT("connect_wifi");

    private final String deviceAction;

    CommandType(String deviceAction) {
        this.deviceAction = deviceAction;
    }

    public String deviceAction() {
        return deviceAction;
    }
}
