package com.netpulse.backend.anomaly.repository;

import java.util.Optional;

import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyStatus;
import com.netpulse.backend.anomaly.entity.AnomalyType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AnomalyRepository extends JpaRepository<Anomaly, Long>, JpaSpecificationExecutor<Anomaly> {

    Optional<Anomaly> findFirstByDeviceIdAndTypeAndStatus(Long deviceId, AnomalyType type, AnomalyStatus status);

    long countByStatus(AnomalyStatus status);

    long countByStatusAndSeverity(AnomalyStatus status, AnomalySeverity severity);

    long countByDeviceIdAndStatus(Long deviceId, AnomalyStatus status);
}
