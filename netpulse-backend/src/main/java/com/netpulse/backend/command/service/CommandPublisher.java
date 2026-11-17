package com.netpulse.backend.command.service;

/** Sends a command to a device. Implemented over MQTT; replaced by a fake in tests. */
public interface CommandPublisher {

    /** @throws CommandDeliveryException if the message could not be handed to the broker */
    void publish(String deviceCode, String payload);
}
