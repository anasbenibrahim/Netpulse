package com.netpulse.backend.command.mqtt;

import java.nio.charset.StandardCharsets;

import com.netpulse.backend.command.service.CommandDeliveryException;
import com.netpulse.backend.command.service.CommandPublisher;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "netpulse.mqtt.enabled", havingValue = "true", matchIfMissing = true)
public class MqttCommandPublisher implements CommandPublisher {

    private final MqttClient mqttClient;
    private final String topicPrefix;

    public MqttCommandPublisher(MqttClient mqttClient, @Value("${mqtt.topic.command-prefix}") String topicPrefix) {
        this.mqttClient = mqttClient;
        this.topicPrefix = topicPrefix;
    }

    @Override
    public void publish(String deviceCode, String payload) {
        if (!mqttClient.isConnected()) {
            throw new CommandDeliveryException("Backend is not connected to the MQTT broker");
        }
        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
        message.setQos(1);
        // Not retained: a Wi-Fi command must never be replayed when the device reconnects later.
        message.setRetained(false);
        try {
            mqttClient.publish(topicPrefix + deviceCode + "/command", message);
        } catch (MqttException e) {
            throw new CommandDeliveryException("MQTT publish failed: " + e.getMessage());
        }
    }
}
