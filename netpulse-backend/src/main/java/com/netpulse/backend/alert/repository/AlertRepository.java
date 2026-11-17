package com.netpulse.backend.alert.repository;

import java.util.Collection;
import java.util.List;

import com.netpulse.backend.alert.entity.Alert;
import com.netpulse.backend.alert.entity.AlertStatus;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AlertRepository extends JpaRepository<Alert, Long>, JpaSpecificationExecutor<Alert> {

    List<Alert> findByAnomalyIdAndStatusNot(Long anomalyId, AlertStatus status);

    long countByStatusIn(Collection<AlertStatus> statuses);
}
