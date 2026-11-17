package com.netpulse.backend.command.service;

public class CommandDeliveryException extends RuntimeException {

    public CommandDeliveryException(String message) {
        super(message);
    }
}
