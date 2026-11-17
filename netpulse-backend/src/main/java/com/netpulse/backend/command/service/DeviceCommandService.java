package com.netpulse.backend.command.service;

import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.netpulse.backend.command.CommandProperties;
import com.netpulse.backend.command.dto.CommandResponse;
import com.netpulse.backend.command.dto.CommandResultMessage;
import com.netpulse.backend.command.dto.WifiConnectRequest;
import com.netpulse.backend.command.dto.WifiNetwork;
import com.netpulse.backend.command.entity.CommandStatus;
import com.netpulse.backend.command.entity.CommandType;
import com.netpulse.backend.command.entity.DeviceCommand;
import com.netpulse.backend.command.repository.DeviceCommandRepository;
import com.netpulse.backend.common.exception.ConflictException;
import com.netpulse.backend.common.exception.ResourceNotFoundException;
import com.netpulse.backend.device.entity.Device;
import com.netpulse.backend.device.entity.DeviceStatus;
import com.netpulse.backend.device.service.DeviceService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Limit;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Dashboard -> backend -> MQTT -> device -> MQTT -> backend.
 * One command at a time per device; the device answers on .../command-result with the command id.
 */
@Service
@Transactional
public class DeviceCommandService {

    private static final Logger log = LoggerFactory.getLogger(DeviceCommandService.class);
    private static final TypeReference<List<WifiNetwork>> NETWORK_LIST = new TypeReference<>() {
    };

    private final DeviceCommandRepository commandRepository;
    private final DeviceService deviceService;
    private final ObjectProvider<CommandPublisher> publisher;
    private final ObjectMapper objectMapper;
    private final CommandProperties properties;
    private final TransactionTemplate transactions;
    private final Clock clock;

    public DeviceCommandService(DeviceCommandRepository commandRepository, DeviceService deviceService,
                                ObjectProvider<CommandPublisher> publisher, ObjectMapper objectMapper,
                                CommandProperties properties, TransactionTemplate transactions, Clock clock) {
        this.commandRepository = commandRepository;
        this.deviceService = deviceService;
        this.publisher = publisher;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommandResponse requestWifiScan(String deviceCode) {
        return send(deviceCode, CommandType.WIFI_SCAN, null, Map.of());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public CommandResponse requestWifiConnect(String deviceCode, WifiConnectRequest request) {
        String password = request.password() == null ? "" : request.password();
        return send(deviceCode, CommandType.WIFI_CONNECT, request.ssid(),
                Map.of("ssid", request.ssid(), "password", password));
    }

    /**
     * The command is committed BEFORE being published, so a fast answer from the
     * device always finds it in the database.
     */
    private CommandResponse send(String deviceCode, CommandType type, String targetSsid, Map<String, String> args) {
        DeviceCommand command = transactions.execute(status -> {
            Device device = deviceService.getByCode(deviceCode);
            if (device.getStatus() != DeviceStatus.ONLINE) {
                throw new ConflictException(deviceCode + " is offline: it cannot receive commands");
            }
            commandRepository.findFirstByDeviceIdAndStatus(device.getId(), CommandStatus.SENT).ifPresent(pending -> {
                throw new ConflictException(
                        "A " + pending.getType() + " command is already in progress on " + deviceCode);
            });
            return commandRepository.save(DeviceCommand.create(device, type, targetSsid, clock.instant()));
        });

        Map<String, String> body = new LinkedHashMap<>();
        body.put("id", command.getId());
        body.put("action", type.deviceAction());
        body.putAll(args);

        try {
            CommandPublisher mqtt = publisher.getIfAvailable();
            if (mqtt == null) {
                throw new CommandDeliveryException("MQTT is disabled");
            }
            mqtt.publish(deviceCode, objectMapper.writeValueAsString(body));
            // Logged without the arguments: they may contain a Wi-Fi password.
            log.info("[COMMAND] {} sent to {} (id={})", type, deviceCode, command.getId());
            return toResponse(command);
        } catch (CommandDeliveryException e) {
            log.warn("[COMMAND] {} to {} not delivered: {}", type, deviceCode, e.getMessage());
            return transactions.execute(status -> {
                DeviceCommand failed = commandRepository.findById(command.getId()).orElseThrow();
                failed.fail(e.getMessage(), clock.instant());
                return toResponse(failed);
            });
        }
    }

    /** Handles the device's answer on netpulse/devices/{code}/command-result. */
    public void handleResult(String deviceCode, String payload) {
        CommandResultMessage result;
        try {
            result = objectMapper.readValue(payload, CommandResultMessage.class);
        } catch (JacksonException e) {
            log.error("[COMMAND] Invalid result from {}: {}", deviceCode, e.getOriginalMessage());
            return;
        }
        if (result.requestId() == null) {
            log.warn("[COMMAND] Result without requestId from {}", deviceCode);
            return;
        }
        commandRepository.findById(result.requestId())
                .filter(command -> command.getDevice().getDeviceCode().equals(deviceCode))
                .filter(command -> command.getStatus() == CommandStatus.SENT)
                .ifPresentOrElse(command -> complete(command, result),
                        () -> log.warn("[COMMAND] Ignoring result for unknown or finished command {}", result.requestId()));
    }

    private void complete(DeviceCommand command, CommandResultMessage result) {
        String networks = null;
        if (result.networks() != null) {
            networks = objectMapper.writeValueAsString(result.networks());
        }
        command.complete(result.succeeded(), result.message(), networks, clock.instant());
        if (command.getType() == CommandType.WIFI_CONNECT && result.succeeded()) {
            deviceService.updateNetworkIdentity(command.getDevice(), result.ip(), result.ssid());
        }
        log.info("[COMMAND] {} on {} -> {} {}", command.getType(), command.getDevice().getDeviceCode(),
                command.getStatus(), result.message() == null ? "" : result.message());
    }

    @Scheduled(fixedDelay = 15_000)
    public void timeOutUnansweredCommands() {
        Instant now = clock.instant();
        commandRepository.findByStatusAndCreatedAtBefore(CommandStatus.SENT, now.minus(properties.timeout()))
                .forEach(command -> {
                    command.timeOut(now);
                    log.warn("[COMMAND] {} on {} timed out", command.getType(), command.getDevice().getDeviceCode());
                });
    }

    @Transactional(readOnly = true)
    public CommandResponse get(String id) {
        return toResponse(commandRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Command", id)));
    }

    @Transactional(readOnly = true)
    public List<CommandResponse> history(String deviceCode, int limit) {
        Device device = deviceService.getByCode(deviceCode);
        return commandRepository.findByDeviceIdOrderByCreatedAtDesc(device.getId(), Limit.of(limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    private CommandResponse toResponse(DeviceCommand command) {
        List<WifiNetwork> networks = null;
        if (command.getResultPayload() != null) {
            try {
                networks = objectMapper.readValue(command.getResultPayload(), NETWORK_LIST);
            } catch (JacksonException e) {
                log.warn("[COMMAND] Stored payload of {} is not a network list", command.getId());
            }
        }
        return CommandResponse.from(command, networks);
    }
}
