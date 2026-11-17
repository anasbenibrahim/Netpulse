package com.netpulse.backend.telemetry.repository;

import java.time.Instant;
import java.util.List;

import com.netpulse.backend.telemetry.entity.SystemTelemetry;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SystemTelemetryRepository extends JpaRepository<SystemTelemetry, Long> {

    List<SystemTelemetry> findByDeviceIdAndReceivedAtAfterOrderByReceivedAtDesc(
            Long deviceId, Instant from, Limit limit);

    @Modifying
    @Query("delete from SystemTelemetry t where t.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
