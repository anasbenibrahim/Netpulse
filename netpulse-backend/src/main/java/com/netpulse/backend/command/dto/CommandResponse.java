package com.netpulse.backend.command.dto;

import java.time.Instant;
import java.util.List;

import com.netpulse.backend.command.entity.CommandStatus;
import com.netpulse.backend.command.entity.CommandType;
import com.netpulse.backend.command.entity.DeviceCommand;

public record CommandResponse(
        String id,
        String deviceCode,
        CommandType type,
        CommandStatus status,
        String targetSsid,
        String message,
        List<WifiNetwork> networks,
        Instant createdAt,
        Instant completedAt
) {
    public static CommandResponse from(DeviceCommand c, List<WifiNetwork> networks) {
        return new CommandResponse(c.getId(), c.getDevice().getDeviceCode(), c.getType(), c.getStatus(),
                c.getTargetSsid(), c.getResultMessage(), networks, c.getCreatedAt(), c.getCompletedAt());
    }
}
