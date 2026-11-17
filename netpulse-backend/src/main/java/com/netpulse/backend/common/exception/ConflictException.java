package com.netpulse.backend.common.exception;

/** The request is valid but cannot be done in the current state (HTTP 409). */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
