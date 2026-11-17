package com.netpulse.backend.command;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import com.netpulse.backend.command.service.CommandDeliveryException;
import com.netpulse.backend.command.service.CommandPublisher;
import com.netpulse.backend.command.service.DeviceCommandService;
import com.netpulse.backend.device.repository.DeviceRepository;
import com.netpulse.backend.telemetry.service.TelemetryIngestionService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Dashboard -> REST -> command published -> device answer -> command status.
 * Not @Transactional: the service commits each command before publishing it.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class WifiCommandIntegrationTest {

    private static final String DEVICE = "esp32-cmd";

    /** Stands in for MQTT: records what would be sent to the device. */
    static class RecordingPublisher implements CommandPublisher {
        final List<String> sent = new ArrayList<>();
        boolean brokerDown;

        @Override
        public void publish(String deviceCode, String payload) {
            if (brokerDown) {
                throw new CommandDeliveryException("Backend is not connected to the MQTT broker");
            }
            sent.add(payload);
        }
    }

    @TestConfiguration
    static class PublisherConfig {
        @Bean
        RecordingPublisher recordingPublisher() {
            return new RecordingPublisher();
        }
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private RecordingPublisher publisher;
    @Autowired private TelemetryIngestionService ingestion;
    @Autowired private DeviceCommandService commandService;
    @Autowired private DeviceRepository deviceRepository;
    @Autowired private ObjectMapper objectMapper;

    @BeforeEach
    void deviceIsOnline() {
        publisher.sent.clear();
        publisher.brokerDown = false;
        ingestion.ingestStatus(DEVICE, "online");
    }

    @AfterEach
    void cleanUp() {
        deviceRepository.deleteAll(); // cascades to commands
    }

    @Test
    void wifiScanRoundTrip() throws Exception {
        String id = startCommand(post("/api/devices/{code}/wifi/scan", DEVICE));

        JsonNode sent = lastSent();
        assertThat(sent.get("id").asString()).isEqualTo(id);
        assertThat(sent.get("action").asString()).isEqualTo("scan");

        commandService.handleResult(DEVICE, """
                {"requestId":"%s","action":"scan","status":"SUCCEEDED","message":"2 networks found",
                 "networks":[{"ssid":"Orange-4D9B","rssi":-62,"channel":6,"security":"WPA2"},
                             {"ssid":"Cafe","rssi":-80,"channel":11,"security":"OPEN"}]}""".formatted(id));

        mockMvc.perform(get("/api/commands/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.networks.length()").value(2))
                .andExpect(jsonPath("$.networks[0].ssid").value("Orange-4D9B"))
                .andExpect(jsonPath("$.networks[1].security").value("OPEN"));
    }

    @Test
    void wifiConnectSendsCredentialsButNeverReturnsThePassword() throws Exception {
        String id = startCommand(post("/api/devices/{code}/wifi/connect", DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ssid\":\"Cafe-WiFi\",\"password\":\"s3cretPass\"}"));

        JsonNode sent = lastSent();
        assertThat(sent.get("action").asString()).isEqualTo("connect_wifi");
        assertThat(sent.get("ssid").asString()).isEqualTo("Cafe-WiFi");
        assertThat(sent.get("password").asString()).isEqualTo("s3cretPass");

        String body = mockMvc.perform(get("/api/commands/{id}", id))
                .andExpect(jsonPath("$.targetSsid").value("Cafe-WiFi"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("s3cretPass");

        commandService.handleResult(DEVICE, """
                {"requestId":"%s","action":"connect_wifi","status":"SUCCEEDED",
                 "message":"Connected to Cafe-WiFi","ssid":"Cafe-WiFi","ip":"10.0.0.42"}""".formatted(id));

        mockMvc.perform(get("/api/commands/{id}", id)).andExpect(jsonPath("$.status").value("SUCCEEDED"));
        mockMvc.perform(get("/api/devices/{code}", DEVICE))
                .andExpect(jsonPath("$.ssid").value("Cafe-WiFi"))
                .andExpect(jsonPath("$.ipAddress").value("10.0.0.42"));
    }

    @Test
    void failedSwitchIsReported() throws Exception {
        String id = startCommand(post("/api/devices/{code}/wifi/connect", DEVICE)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ssid\":\"Cafe-WiFi\",\"password\":\"wrongPass\"}"));

        commandService.handleResult(DEVICE, """
                {"requestId":"%s","action":"connect_wifi","status":"FAILED",
                 "message":"Could not join Cafe-WiFi; back on Orange-4D9B"}""".formatted(id));

        mockMvc.perform(get("/api/commands/{id}", id))
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Could not join Cafe-WiFi; back on Orange-4D9B"));
    }

    @Test
    void onlyOneCommandAtATime() throws Exception {
        startCommand(post("/api/devices/{code}/wifi/scan", DEVICE));

        mockMvc.perform(post("/api/devices/{code}/wifi/scan", DEVICE))
                .andExpect(status().isConflict());
    }

    @Test
    void rejectsInvalidCredentials() throws Exception {
        mockMvc.perform(post("/api/devices/{code}/wifi/connect", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssid\":\"Cafe\",\"password\":\"short\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("password must be empty (open network) or 8 to 63 characters"));

        mockMvc.perform(post("/api/devices/{code}/wifi/connect", DEVICE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"ssid\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest());
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    void offlineDeviceCannotReceiveCommands() throws Exception {
        ingestion.ingestStatus(DEVICE, "offline");

        mockMvc.perform(post("/api/devices/{code}/wifi/scan", DEVICE))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(DEVICE + " is offline: it cannot receive commands"));
    }

    @Test
    void brokerDownMarksCommandFailed() throws Exception {
        publisher.brokerDown = true;

        mockMvc.perform(post("/api/devices/{code}/wifi/scan", DEVICE))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.message").value("Backend is not connected to the MQTT broker"));
    }

    private String startCommand(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        String body = mockMvc.perform(request)
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("SENT"))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asString();
    }

    private JsonNode lastSent() {
        assertThat(publisher.sent).isNotEmpty();
        return objectMapper.readTree(publisher.sent.getLast());
    }
}
