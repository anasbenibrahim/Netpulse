package com.netpulse.backend.config;

import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
// Disabled in tests (netpulse.mqtt.enabled=false) so they never touch the real broker.
@ConditionalOnProperty(name = "netpulse.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttConfig {

    @Value("${mqtt.broker-url}")
    private String brokerUrl;

    @Value("${mqtt.client-id}")
    private String clientId;

    /**
     * The client is created here but connected by TelemetryMqttConsumer, which also
     * handles retries, resubscription and shutdown. destroyMethod = "" stops
     * Spring from calling close() on a still-connected client (Paho error 32100).
     */
    @Bean(destroyMethod = "")
    public MqttClient mqttClient() throws MqttException {
        // A unique suffix keeps two running instances (e.g. the app and the tests)
        // from kicking each other off the broker with the same client id.
        String uniqueClientId = clientId + "-" + UUID.randomUUID().toString().substring(0, 8);
        return new MqttClient(brokerUrl, uniqueClientId, new MemoryPersistence());
    }

    @Bean
    public MqttConnectOptions mqttConnectOptions() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        options.setConnectionTimeout(10);
        options.setKeepAliveInterval(30);
        return options;
    }
}
