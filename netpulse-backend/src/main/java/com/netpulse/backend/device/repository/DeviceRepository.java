package com.netpulse.backend.device.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.entity.DeviceStatus;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    Optional<Device> findByDeviceCode(String deviceCode);

    List<Device> findAllByOrderByDeviceCodeAsc();

    List<Device> findByStatusAndLastSeenBefore(DeviceStatus status, Instant cutoff);

    long countByStatus(DeviceStatus status);
}
