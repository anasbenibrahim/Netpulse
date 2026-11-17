package com.netpulse.backend.telemetry.mqtt;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import com.netpulse.backend.command.service.DeviceCommandService;
import com.netpulse.backend.telemetry.service.TelemetryIngestionService;

import jakarta.annotation.PreDestroy;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Owns the MQTT connection: connects (retrying until the broker is reachable),
 * subscribes to the device topics, and resubscribes after every reconnect —
 * with cleanSession=true the broker forgets subscriptions when the link drops.
 */
@Component
@ConditionalOnProperty(name = "netpulse.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class TelemetryMqttConsumer implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(TelemetryMqttConsumer.class);
    private static final long RETRY_DELAY_SECONDS = 5;

    private final MqttClient mqttClient;
    private final MqttConnectOptions connectOptions;
    private final TelemetryIngestionService ingestionService;
    private final DeviceCommandService commandService;

    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "mqtt-connector");
        thread.setDaemon(true);
        return thread;
    });

    @Value("${mqtt.topic.telemetry}")
    private String telemetryTopic;

    @Value("${mqtt.topic.network}")
    private String networkTopic;

    @Value("${mqtt.topic.status}")
    private String statusTopic;

    @Value("${mqtt.topic.command-result}")
    private String commandResultTopic;

    public TelemetryMqttConsumer(MqttClient mqttClient, MqttConnectOptions connectOptions,
                                 TelemetryIngestionService ingestionService,
                                 DeviceCommandService commandService) {
        this.mqttClient = mqttClient;
        this.connectOptions = connectOptions;
        this.ingestionService = ingestionService;
        this.commandService = commandService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        mqttClient.setCallback(this);
        executor.execute(this::connect);
    }

    // Paho's automatic reconnect only kicks in after a first successful
    // connection, so the initial attempt is retried here.
    private void connect() {
        try {
            log.info("[MQTT] Connecting to {} ...", mqttClient.getServerURI());
            mqttClient.connect(connectOptions);
        } catch (MqttException e) {
            log.warn("[MQTT] Connection to {} failed: {}. Retrying in {}s",
                    mqttClient.getServerURI(), e.getMessage(), RETRY_DELAY_SECONDS);
            if (!executor.isShutdown()) {
                executor.schedule(this::connect, RETRY_DELAY_SECONDS, TimeUnit.SECONDS);
            }
        }
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        log.info("[MQTT] {} to {}", reconnect ? "Reconnected" : "Connected", serverURI);
        // This runs on Paho's callback thread; subscribe() blocks, so hand it off.
        if (!executor.isShutdown()) {
            executor.execute(this::subscribeToTopics);
        }
    }

    private void subscribeToTopics() {
        try {
            subscribe(telemetryTopic, ingestionService::ingestTelemetry);
            subscribe(networkTopic, ingestionService::ingestNetworkMetrics);
            subscribe(statusTopic, ingestionService::ingestStatus);
            subscribe(commandResultTopic, commandService::handleResult);
            log.info("[MQTT] All subscriptions initialized!");
        } catch (MqttException e) {
            // If the link dropped meanwhile, the next connectComplete() retries.
            log.error("[MQTT] Subscription failed: {}", e.getMessage());
        }
    }

    /** Subscribes with QoS 1 and passes (deviceCode, payload) to the handler. */
    private void subscribe(String topicFilter, BiConsumer<String, String> handler) throws MqttException {
        mqttClient.subscribe(topicFilter, 1, (topic, message) -> {
            try {
                handler.accept(deviceCodeFrom(topic), payloadOf(message));
            } catch (Exception e) {
                // Never let an exception escape: Paho drops the connection if a listener throws.
                log.error("[MQTT] Failed to process message on {}: {}", topic, e.getMessage(), e);
            }
        });
    }

    /** netpulse/devices/{deviceCode}/{kind} -> deviceCode */
    static String deviceCodeFrom(String topic) {
        String[] parts = topic.split("/");
        return parts.length >= 2 ? parts[parts.length - 2] : topic;
    }

    private static String payloadOf(MqttMessage message) {
        return new String(message.getPayload(), StandardCharsets.UTF_8);
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("[MQTT] Connection lost: {}. Reconnecting automatically...", cause.getMessage());
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        // Messages are handled by the per-topic listeners registered in subscribeToTopics().
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
        // This client only subscribes; nothing is published.
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (mqttClient.isConnected()) {
                mqttClient.disconnect();
            }
            mqttClient.close();
            log.info("[MQTT] Disconnected.");
        } catch (MqttException e) {
            log.warn("[MQTT] Error during shutdown: {}", e.getMessage());
        }
    }
}
