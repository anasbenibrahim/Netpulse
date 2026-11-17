package com.netpulse.backend.anomaly.service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.netpulse.backend.alert.service.AlertService;
import com.netpulse.backend.anomaly.detector.AnomalyDetector;
import com.netpulse.backend.anomaly.detector.AnomalyProperties;
import com.netpulse.backend.anomaly.detector.Evaluation;
import com.netpulse.backend.anomaly.dto.AnomalyResponse;
import com.netpulse.backend.anomaly.entity.Anomaly;
import com.netpulse.backend.anomaly.entity.AnomalySeverity;
import com.netpulse.backend.anomaly.entity.AnomalyStatus;
import com.netpulse.backend.anomaly.entity.AnomalyType;
import com.netpulse.backend.anomaly.repository.AnomalyRepository;
import com.netpulse.backend.device.entity.Device;

import jakarta.persistence.criteria.Predicate;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns rule evaluations into anomaly episodes: one ACTIVE anomaly per device and
 * type at a time, resolved after {@code resolveAfter} consecutive normal readings.
 */
@Service
@Transactional
public class AnomalyService {

    private static final Logger log = LoggerFactory.getLogger(AnomalyService.class);

    private final AnomalyRepository anomalyRepository;
    private final AnomalyDetector detector;
    private final AlertService alertService;
    private final AnomalyProperties properties;
    private final Clock clock;

    public AnomalyService(AnomalyRepository anomalyRepository, AnomalyDetector detector,
                          AlertService alertService, AnomalyProperties properties, Clock clock) {
        this.anomalyRepository = anomalyRepository;
        this.detector = detector;
        this.alertService = alertService;
        this.properties = properties;
        this.clock = clock;
    }

    public void apply(Device device, List<Evaluation> evaluations) {
        for (Evaluation evaluation : evaluations) {
            if (evaluation.isViolation()) {
                recordViolation(device, evaluation);
            } else {
                recordNormal(device, evaluation.type());
            }
        }
    }

    public void deviceWentOffline(Device device, String reason) {
        recordViolation(device, detector.deviceOffline(reason));
    }

    /** A device that talks again is online: no need to wait for several readings. */
    public void deviceBackOnline(Device device) {
        findActive(device, AnomalyType.DEVICE_OFFLINE).ifPresent(this::resolve);
    }

    private void recordViolation(Device device, Evaluation evaluation) {
        var active = findActive(device, evaluation.type());
        if (active.isEmpty()) {
            Anomaly anomaly = anomalyRepository.save(Anomaly.open(device, evaluation, clock.instant()));
            log.warn("[ANOMALY] {} opened on {}: {}", anomaly.getSeverity(), device.getDeviceCode(),
                    anomaly.getDescription());
            alertService.raise(anomaly, false);
        } else if (active.get().recordOccurrence(evaluation, clock.instant())) {
            alertService.raise(active.get(), true);
        }
    }

    private void recordNormal(Device device, AnomalyType type) {
        findActive(device, type).ifPresent(anomaly -> {
            if (anomaly.recordNormalReading() >= properties.resolveAfter()) {
                resolve(anomaly);
            }
        });
    }

    private void resolve(Anomaly anomaly) {
        anomaly.resolve(clock.instant());
        alertService.resolveFor(anomaly);
        log.info("[ANOMALY] Resolved on {}: {}", anomaly.getDevice().getDeviceCode(), anomaly.getType().label());
    }

    private Optional<Anomaly> findActive(Device device, AnomalyType type) {
        return anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(device.getId(), type, AnomalyStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public List<AnomalyResponse> list(AnomalyStatus status, String deviceCode, int limit) {
        Specification<Anomaly> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (deviceCode != null && !deviceCode.isBlank()) {
                predicates.add(cb.equal(root.get("device").get("deviceCode"), deviceCode));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
        return anomalyRepository
                .findAll(spec, PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "lastDetectedAt")))
                .map(AnomalyResponse::from)
                .getContent();
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return anomalyRepository.countByStatus(AnomalyStatus.ACTIVE);
    }

    @Transactional(readOnly = true)
    public long countActiveCritical() {
        return anomalyRepository.countByStatusAndSeverity(AnomalyStatus.ACTIVE, AnomalySeverity.CRITICAL);
    }

    @Transactional(readOnly = true)
    public long countActive(Device device) {
        return anomalyRepository.countByDeviceIdAndStatus(device.getId(), AnomalyStatus.ACTIVE);
    }
}
