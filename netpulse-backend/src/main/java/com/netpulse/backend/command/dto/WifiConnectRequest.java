package com.netpulse.backend.command.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Wi-Fi credentials typed in the dashboard. Limits are those of WPA2 (SSID 32, password 8-63). */
public record WifiConnectRequest(
        @NotBlank @Size(max = 32) String ssid,
        @Size(max = 63) String password
) {
    /** Open networks have no password; secured ones need at least 8 characters. */
    @AssertTrue(message = "password must be empty (open network) or 8 to 63 characters")
    public boolean isPasswordValid() {
        return password == null || password.isEmpty() || password.length() >= 8;
    }

    // Never print the password, even in logs or exception messages.
    @Override
    public String toString() {
        return "WifiConnectRequest[ssid=" + ssid + ", password=***]";
    }
}
