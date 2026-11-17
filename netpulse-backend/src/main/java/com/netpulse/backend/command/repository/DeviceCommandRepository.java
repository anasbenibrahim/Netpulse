package com.netpulse.backend.command.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.netpulse.backend.command.entity.CommandStatus;
import com.netpulse.backend.command.entity.DeviceCommand;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, String> {

    List<DeviceCommand> findByDeviceIdOrderByCreatedAtDesc(Long deviceId, Limit limit);

    Optional<DeviceCommand> findFirstByDeviceIdAndStatus(Long deviceId, CommandStatus status);

    List<DeviceCommand> findByStatusAndCreatedAtBefore(CommandStatus status, Instant cutoff);
}
