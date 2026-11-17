package com.netpulse.backend.alert.service;

import java.time.Clock;
import java.util.EnumSet;
import java.util.List;

import com.netpulse.backend.alert.dto.AlertResponse;
import com.netpulse.backend.alert.entity.Alert;
import com.netpulse.backend.alert.entity.AlertStatus;
import com.netpulse.backend.alert.repository.AlertRepository;
import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.common.exception.ResourceNotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AlertService {

    private static final Logger log = LoggerFactory.getLogger(AlertService.class);

    private final AlertRepository alertRepository;
    private final Clock clock;

    public AlertService(AlertRepository alertRepository, Clock clock) {
        this.alertRepository = alertRepository;
        this.clock = clock;
    }

    /** Raises an alert for a new anomaly, or for one whose severity just escalated. */
    public void raise(Anomaly anomaly, boolean escalation) {
        String title = (escalation ? "[ESCALATED] " : "")
                + anomaly.getDevice().getDeviceCode() + ": " + anomaly.getType().label();
        Alert alert = alertRepository.save(Alert.raise(anomaly, title, clock.instant()));
        log.warn("[ALERT] {} {} - {}", alert.getSeverity(), title, alert.getMessage());
    }

    /** Closes every unresolved alert of an anomaly that went back to normal. */
    public void resolveFor(Anomaly anomaly) {
        alertRepository.findByAnomalyIdAndStatusNot(anomaly.getId(), AlertStatus.RESOLVED)
                .forEach(alert -> alert.resolve(clock.instant()));
    }

    public AlertResponse acknowledge(Long id) {
        Alert alert = get(id);
        alert.acknowledge(clock.instant());
        return AlertResponse.from(alert);
    }

    public AlertResponse resolve(Long id) {
        Alert alert = get(id);
        alert.resolve(clock.instant());
        return AlertResponse.from(alert);
    }

    @Transactional(readOnly = true)
    public List<AlertResponse> list(AlertStatus status, int limit) {
        Specification<Alert> spec = (root, query, cb) ->
                status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
        return alertRepository.findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")))
                .map(AlertResponse::from)
                .getContent();
    }

    /** Alerts still needing attention (open or acknowledged). */
    @Transactional(readOnly = true)
    public long countUnresolved() {
        return alertRepository.countByStatusIn(EnumSet.of(AlertStatus.OPEN, AlertStatus.ACKNOWLEDGED));
    }

    private Alert get(Long id) {
        return alertRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Alert", id));
    }
}
