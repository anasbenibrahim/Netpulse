package com.netpulse.backend.command.entity;

import java.time.Instant;
import java.util.UUID;

import com.netpulse.backend.device.entity.Device;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A command sent to a device. The id travels in the MQTT message so the answer can be matched. */
@Entity
@Table(name = "device_commands")
@Getter
@NoArgsConstructor
public class DeviceCommand {

    public static final int MAX_PAYLOAD = 8192;

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "device_id", nullable = false)
    private Device device;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CommandType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommandStatus status;

    @Column(name = "target_ssid", length = 64)
    private String targetSsid;

    @Column(name = "result_message", length = 512)
    private String resultMessage;

    /** Raw JSON returned by the device, e.g. the list of scanned networks. */
    @Column(name = "result_payload", length = MAX_PAYLOAD)
    private String resultPayload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    public static DeviceCommand create(Device device, CommandType type, String targetSsid, Instant now) {
        DeviceCommand command = new DeviceCommand();
        command.id = UUID.randomUUID().toString();
        command.device = device;
        command.type = type;
        command.status = CommandStatus.SENT;
        command.targetSsid = targetSsid;
        command.createdAt = now;
        return command;
    }

    public void complete(boolean success, String message, String payload, Instant now) {
        status = success ? CommandStatus.SUCCEEDED : CommandStatus.FAILED;
        resultMessage = truncate(message, 512);
        resultPayload = truncate(payload, MAX_PAYLOAD);
        completedAt = now;
    }

    public void fail(String message, Instant now) {
        complete(false, message, null, now);
    }

    public void timeOut(Instant now) {
        status = CommandStatus.TIMED_OUT;
        resultMessage = "No answer from the device";
        completedAt = now;
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }
}
