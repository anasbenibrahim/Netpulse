package com.netpulse.backend.telemetry.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.netpulse.backend.telemetry.entity.NetworkMetric;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NetworkMetricRepository extends JpaRepository<NetworkMetric, Long> {

    List<NetworkMetric> findByDeviceIdAndReceivedAtAfterOrderByReceivedAtDesc(
            Long deviceId, Instant from, Limit limit);

    Optional<NetworkMetric> findFirstByDeviceIdOrderByReceivedAtDesc(Long deviceId);

    @Modifying
    @Query("delete from NetworkMetric m where m.receivedAt < :cutoff")
    int deleteOlderThan(@Param("cutoff") Instant cutoff);
}
