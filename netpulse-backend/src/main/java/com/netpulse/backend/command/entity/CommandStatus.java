package com.netpulse.backend.command.entity;

public enum CommandStatus {
    /** Published to the broker, waiting for the device's answer. */
    SENT,
    SUCCEEDED,
    FAILED,
    /** The device never answered (offline, rebooted, or out of reach). */
    TIMED_OUT;

    public boolean isFinished() {
        return this != SENT;
    }
}
