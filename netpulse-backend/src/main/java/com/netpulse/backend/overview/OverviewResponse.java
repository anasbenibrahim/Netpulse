package com.netpulse.backend.overview;

import java.util.List;

import com.netpulse.backend.alert.dto.AlertResponse;

public record OverviewResponse(
        long totalDevices,
        long onlineDevices,
        long offlineDevices,
        long activeAnomalies,
        long criticalAnomalies,
        long unresolvedAlerts,
        List<AlertResponse> recentAlerts
) {
}
