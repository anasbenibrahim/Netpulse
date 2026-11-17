package com.netpulse.backend.common;

public final class Limits {

    private Limits() {
    }

    /** Keeps a client-supplied page size within [1, max]. */
    public static int clamp(int requested, int max) {
        return Math.max(1, Math.min(requested, max));
    }
}
