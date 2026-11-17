#include <Arduino.h>
#include <WiFi.h>
#include <ESP32Ping.h>
#include <PubSubClient.h>
#include "secrets.h"

// ==========================================
// NETPULSE CONFIGURATION
// ==========================================

const unsigned long MONITORING_INTERVAL = 5000;
unsigned long lastMonitoringTime = 0;

// ==========================================
// MQTT CONFIGURATION
// ==========================================

const char* MQTT_BROKER = "192.168.1.101";
const int MQTT_PORT = 1883;

const char* MQTT_CLIENT_ID = "netpulse-esp32-01";

const char* MQTT_TELEMETRY_TOPIC =
    "netpulse/devices/esp32-01/telemetry";

const char* MQTT_NETWORK_TOPIC =
    "netpulse/devices/esp32-01/network";

const char* MQTT_STATUS_TOPIC =
    "netpulse/devices/esp32-01/status";

WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL = 5000;

// ==========================================
// NETWORK METRICS
// ==========================================

float currentRSSI = -100;

float gatewayLatency = -1;
float gatewayPacketLoss = -1;

float internetLatency = -1;
float internetPacketLoss = -1;

bool gatewayMetricsAvailable = false;
bool gatewayLatencyAvailable = false;

bool internetMetricsAvailable = false;
bool internetLatencyAvailable = false;

float networkHealthScore = 0;
String networkStatus = "UNKNOWN";

// ==========================================
// WIFI CONNECTION
// ==========================================

void connectToWiFi() {

    Serial.println();
    Serial.println("========== NETPULSE WIFI CONNECTION ==========");

    WiFi.mode(WIFI_STA);
    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    int attempts = 0;

    while (WiFi.status() != WL_CONNECTED && attempts < 30) {

        delay(500);
        Serial.print(".");

        attempts++;
    }

    Serial.println();

    if (WiFi.status() == WL_CONNECTED) {

        Serial.println("[SUCCESS] WiFi connected!");

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("Gateway: ");
        Serial.println(WiFi.gatewayIP());

        Serial.print("RSSI: ");
        Serial.println(WiFi.RSSI());

    } else {

        Serial.println("[ERROR] WiFi connection failed!");
    }

    Serial.println("==============================================");
}

// ==========================================
// WIFI RECONNECTION
// ==========================================

void checkWiFiConnection() {

    static unsigned long lastReconnectAttempt = 0;

    if (WiFi.status() != WL_CONNECTED) {

        currentRSSI = -100;

        gatewayMetricsAvailable = false;
        gatewayLatencyAvailable = false;

        internetMetricsAvailable = false;
        internetLatencyAvailable = false;

        gatewayLatency = -1;
        gatewayPacketLoss = -1;

        internetLatency = -1;
        internetPacketLoss = -1;

        if (millis() - lastReconnectAttempt >= 10000) {

            lastReconnectAttempt = millis();

            Serial.println("[WARNING] WiFi disconnected!");
            Serial.println("[INFO] Reconnecting WiFi...");

            WiFi.disconnect();
            WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
        }
    }
}

// ==========================================
// HARDWARE MONITORING
// ==========================================

void monitorHardware() {

    uint32_t freeHeap = ESP.getFreeHeap();
    uint32_t totalHeap = ESP.getHeapSize();

    float heapUsage = 0;

    if (totalHeap > 0) {

        heapUsage =
            ((float)(totalHeap - freeHeap) / totalHeap) * 100.0;
    }

    Serial.println();
    Serial.println("========== NETPULSE TELEMETRY ==========");

    Serial.print("Uptime: ");
    Serial.print(millis() / 1000);
    Serial.println(" seconds");

    Serial.print("Free Heap: ");
    Serial.println(freeHeap);

    Serial.print("Total Heap: ");
    Serial.println(totalHeap);

    Serial.print("Heap Usage: ");
    Serial.print(heapUsage, 2);
    Serial.println(" %");

    Serial.print("Flash Size: ");
    Serial.print(ESP.getFlashChipSize() / 1024 / 1024);
    Serial.println(" MB");

    Serial.println("========================================");
}

// ==========================================
// WIFI MONITORING
// ==========================================

void monitorWiFi() {

    if (WiFi.status() == WL_CONNECTED) {

        currentRSSI = WiFi.RSSI();

        Serial.println();
        Serial.println("========== WIFI TELEMETRY ==========");

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("Gateway: ");
        Serial.println(WiFi.gatewayIP());

        Serial.print("Subnet Mask: ");
        Serial.println(WiFi.subnetMask());

        Serial.print("RSSI: ");
        Serial.print(currentRSSI);
        Serial.println(" dBm");

        Serial.println("====================================");

    } else {

        currentRSSI = -100;

        Serial.println("[WARNING] WiFi disconnected!");
    }
}

// ==========================================
// GATEWAY MONITORING
// ==========================================

void collectNetworkMetrics() {

    Serial.println();
    Serial.println("========== NETPULSE NETWORK DIAGNOSTICS ==========");

    if (WiFi.status() != WL_CONNECTED) {

        gatewayMetricsAvailable = false;
        gatewayLatencyAvailable = false;

        gatewayLatency = -1;
        gatewayPacketLoss = -1;

        Serial.println("Gateway unavailable.");

        return;
    }

    IPAddress gateway = WiFi.gatewayIP();

    Serial.print("Gateway: ");
    Serial.println(gateway);

    const int PING_COUNT = 10;

    int packetsReceived = 0;
    int packetsLost = 0;

    float totalLatency = 0;

    for (int i = 0; i < PING_COUNT; i++) {

        Serial.print("[GATEWAY PING] ");
        Serial.print(i + 1);
        Serial.print("/");
        Serial.println(PING_COUNT);

        bool success = Ping.ping(gateway, 1);

        if (success) {

            packetsReceived++;

            float latency = Ping.averageTime();

            totalLatency += latency;

            Serial.print("[SUCCESS] ");
            Serial.print(latency, 2);
            Serial.println(" ms");

        } else {

            packetsLost++;

            Serial.println("[ERROR] Timeout");
        }

        delay(500);
    }

    gatewayPacketLoss =
        100.0f * packetsLost / PING_COUNT;

    gatewayMetricsAvailable = true;

    if (packetsReceived > 0) {

        gatewayLatency = totalLatency / packetsReceived;
        gatewayLatencyAvailable = true;

    } else {

        gatewayLatency = -1;
        gatewayLatencyAvailable = false;
    }

    Serial.println();
    Serial.println("---------- GATEWAY RESULTS ----------");

    Serial.print("Packets Received: ");
    Serial.println(packetsReceived);

    Serial.print("Packets Lost: ");
    Serial.println(packetsLost);

    Serial.print("Packet Loss: ");
    Serial.print(gatewayPacketLoss, 2);
    Serial.println(" %");

    if (gatewayLatencyAvailable) {

        Serial.print("Gateway Latency: ");
        Serial.print(gatewayLatency, 2);
        Serial.println(" ms");

    } else {

        Serial.println("Gateway unreachable.");
    }

    Serial.println("=====================================");
}

// ==========================================
// INTERNET MONITORING
// ==========================================

void monitorInternetConnectivity() {

    Serial.println();
    Serial.println("========== NETPULSE INTERNET MONITORING ==========");

    if (WiFi.status() != WL_CONNECTED) {

        internetMetricsAvailable = false;
        internetLatencyAvailable = false;

        internetLatency = -1;
        internetPacketLoss = -1;

        Serial.println("Internet unavailable.");

        return;
    }

    IPAddress target(1, 1, 1, 1);

    const int PING_COUNT = 5;

    int packetsReceived = 0;
    int packetsLost = 0;

    float totalLatency = 0;

    for (int i = 0; i < PING_COUNT; i++) {

        Serial.print("[INTERNET PING] ");
        Serial.print(i + 1);
        Serial.print("/");
        Serial.println(PING_COUNT);

        bool success = Ping.ping(target, 1);

        if (success) {

            packetsReceived++;

            float latency = Ping.averageTime();

            totalLatency += latency;

            Serial.print("[SUCCESS] ");
            Serial.print(latency, 2);
            Serial.println(" ms");

        } else {

            packetsLost++;

            Serial.println("[ERROR] Timeout");
        }

        delay(500);
    }

    internetPacketLoss =
        100.0f * packetsLost / PING_COUNT;

    internetMetricsAvailable = true;

    if (packetsReceived > 0) {

        internetLatency = totalLatency / packetsReceived;
        internetLatencyAvailable = true;

    } else {

        internetLatency = -1;
        internetLatencyAvailable = false;
    }

    Serial.println();
    Serial.println("---------- INTERNET RESULTS ----------");

    Serial.print("Packets Received: ");
    Serial.println(packetsReceived);

    Serial.print("Packets Lost: ");
    Serial.println(packetsLost);

    Serial.print("Packet Loss: ");
    Serial.print(internetPacketLoss, 2);
    Serial.println(" %");

    if (internetLatencyAvailable) {

        Serial.print("Average Latency: ");
        Serial.print(internetLatency, 2);
        Serial.println(" ms");

        Serial.println("Internet Status: CONNECTED");

    } else {

        Serial.println("Internet Status: UNAVAILABLE");
    }

    Serial.println("=====================================");
}

// ==========================================
// NETWORK HEALTH SCORING
// ==========================================

float calculateRSSIScore(float rssi) {

    if (rssi >= -50) return 100;
    if (rssi >= -60) return 90;
    if (rssi >= -67) return 80;
    if (rssi >= -70) return 70;
    if (rssi >= -75) return 55;
    if (rssi >= -80) return 35;
    if (rssi >= -85) return 15;

    return 0;
}

float calculateLatencyScore(float latency) {

    if (latency < 0) return 0;

    if (latency <= 5) return 100;
    if (latency <= 10) return 90;
    if (latency <= 20) return 75;
    if (latency <= 50) return 50;
    if (latency <= 100) return 25;

    return 0;
}

float calculatePacketLossScore(float loss) {

    if (loss < 0) return 0;

    if (loss == 0) return 100;
    if (loss <= 1) return 90;
    if (loss <= 3) return 75;
    if (loss <= 5) return 60;
    if (loss <= 10) return 30;

    return 0;
}

// ==========================================
// NETWORK HEALTH SCORE
// ==========================================

void calculateNetworkHealthScore() {

    Serial.println();
    Serial.println("========== NETPULSE NETWORK HEALTH ==========");

    if (WiFi.status() != WL_CONNECTED) {

        networkHealthScore = 0;
        networkStatus = "CRITICAL";

        Serial.println("NETWORK HEALTH SCORE: 0/100");
        Serial.println("NETWORK STATUS: CRITICAL");

        return;
    }

    float totalScore = 0;
    int totalWeight = 0;

    // RSSI: 20%

    totalScore += calculateRSSIScore(currentRSSI) * 20;
    totalWeight += 20;

    // Gateway packet loss: 25%

    if (gatewayMetricsAvailable) {

        totalScore +=
            calculatePacketLossScore(gatewayPacketLoss) * 25;

        totalWeight += 25;
    }

    // Gateway latency: 15%

    if (gatewayLatencyAvailable) {

        totalScore +=
            calculateLatencyScore(gatewayLatency) * 15;

        totalWeight += 15;
    }

    // Internet packet loss: 25%

    if (internetMetricsAvailable) {

        totalScore +=
            calculatePacketLossScore(internetPacketLoss) * 25;

        totalWeight += 25;
    }

    // Internet latency: 15%

    if (internetLatencyAvailable) {

        totalScore +=
            calculateLatencyScore(internetLatency) * 15;

        totalWeight += 15;
    }

    if (totalWeight == 0) {

        networkHealthScore = 0;
        networkStatus = "UNKNOWN";

        Serial.println("Network Health: UNKNOWN");

        return;
    }

    networkHealthScore = totalScore / totalWeight;

    if (networkHealthScore >= 90) {

        networkStatus = "EXCELLENT";

    } else if (networkHealthScore >= 75) {

        networkStatus = "GOOD";

    } else if (networkHealthScore >= 60) {

        networkStatus = "FAIR";

    } else if (networkHealthScore >= 40) {

        networkStatus = "POOR";

    } else {

        networkStatus = "CRITICAL";
    }

    Serial.print("WiFi RSSI: ");
    Serial.print(currentRSSI);
    Serial.println(" dBm");

    Serial.print("Gateway Latency: ");
    Serial.println(gatewayLatency);

    Serial.print("Gateway Packet Loss: ");
    Serial.println(gatewayPacketLoss);

    Serial.print("Internet Latency: ");
    Serial.println(internetLatency);

    Serial.print("Internet Packet Loss: ");
    Serial.println(internetPacketLoss);

    Serial.println("---------------------------------------------");

    Serial.print("NETWORK HEALTH SCORE: ");
    Serial.print(networkHealthScore, 2);
    Serial.println("/100");

    Serial.print("NETWORK STATUS: ");
    Serial.println(networkStatus);

    Serial.println("=============================================");
}

// ==========================================
// MQTT CONNECTION
// ==========================================

void connectToMQTT() {

    if (WiFi.status() != WL_CONNECTED) {
        return;
    }

    if (mqttClient.connected()) {
        return;
    }

    if (millis() - lastMqttReconnectAttempt <
        MQTT_RECONNECT_INTERVAL) {
        return;
    }

    lastMqttReconnectAttempt = millis();

    Serial.println();
    Serial.println("[MQTT] Connecting to broker...");

    bool connected = mqttClient.connect(
        MQTT_CLIENT_ID,
        MQTT_STATUS_TOPIC,
        1,
        true,
        "offline"
    );

    if (connected) {

        Serial.println("[MQTT] Connected successfully!");

        mqttClient.publish(
            MQTT_STATUS_TOPIC,
            "online",
            true
        );

    } else {

        Serial.print("[MQTT] Connection failed. State: ");
        Serial.println(mqttClient.state());
    }
}

// ==========================================
// MQTT JSON HELPERS
// ==========================================

String jsonFloat(float value, bool available) {

    if (!available) {
        return "null";
    }

    return String(value, 2);
}

// ==========================================
// MQTT TELEMETRY PUBLISH
// ==========================================

void publishTelemetry() {

    if (!mqttClient.connected()) {
        return;
    }

    uint32_t freeHeap = ESP.getFreeHeap();
    uint32_t totalHeap = ESP.getHeapSize();

    float heapUsage = 0;

    if (totalHeap > 0) {

        heapUsage =
            ((float)(totalHeap - freeHeap) / totalHeap) * 100.0;
    }

    String payload;
    payload.reserve(512);

    payload = "{";

    payload += "\"deviceId\":\"esp32-01\",";
    payload += "\"uptime\":";
    payload += String(millis() / 1000);
    payload += ",";

    payload += "\"freeHeap\":";
    payload += String(freeHeap);
    payload += ",";

    payload += "\"totalHeap\":";
    payload += String(totalHeap);
    payload += ",";

    payload += "\"heapUsage\":";
    payload += String(heapUsage, 2);
    payload += ",";

    payload += "\"flashSizeMB\":";
    payload += String(ESP.getFlashChipSize() / 1024 / 1024);
    payload += ",";

    payload += "\"ssid\":\"";
    payload += WiFi.status() == WL_CONNECTED ? WiFi.SSID() : "";
    payload += "\",";

    payload += "\"ip\":\"";
    payload += WiFi.status() == WL_CONNECTED ?
        WiFi.localIP().toString() : "";
    payload += "\",";

    payload += "\"rssi\":";
    payload += String(currentRSSI, 2);

    payload += "}";

    bool success = mqttClient.publish(
        MQTT_TELEMETRY_TOPIC,
        payload.c_str()
    );

    if (success) {

        Serial.println("[MQTT] Telemetry published!");

        Serial.println(payload);

    } else {

        Serial.println("[MQTT] Telemetry publish failed!");
    }
}

// ==========================================
// MQTT NETWORK PUBLISH
// ==========================================

void publishNetworkMetrics() {

    if (!mqttClient.connected()) {
        return;
    }

    String payload;
    payload.reserve(512);

    payload = "{";

    payload += "\"deviceId\":\"esp32-01\",";

    payload += "\"rssi\":";
    payload += String(currentRSSI, 2);
    payload += ",";

    payload += "\"gatewayLatency\":";
    payload += jsonFloat(
        gatewayLatency,
        gatewayLatencyAvailable
    );
    payload += ",";

    payload += "\"gatewayPacketLoss\":";
    payload += jsonFloat(
        gatewayPacketLoss,
        gatewayMetricsAvailable
    );
    payload += ",";

    payload += "\"internetLatency\":";
    payload += jsonFloat(
        internetLatency,
        internetLatencyAvailable
    );
    payload += ",";

    payload += "\"internetPacketLoss\":";
    payload += jsonFloat(
        internetPacketLoss,
        internetMetricsAvailable
    );
    payload += ",";

    payload += "\"networkHealthScore\":";
    payload += String(networkHealthScore, 2);
    payload += ",";

    payload += "\"networkStatus\":\"";
    payload += networkStatus;
    payload += "\"";

    payload += "}";

    bool success = mqttClient.publish(
        MQTT_NETWORK_TOPIC,
        payload.c_str()
    );

    if (success) {

        Serial.println("[MQTT] Network metrics published!");

        Serial.println(payload);

    } else {

        Serial.println("[MQTT] Network publish failed!");
    }
}

// ==========================================
// MQTT PUBLISH ALL
// ==========================================

void publishAllMetrics() {

    if (!mqttClient.connected()) {

        Serial.println("[MQTT] Broker not connected. Skipping publish.");

        return;
    }

    publishTelemetry();
    publishNetworkMetrics();

    mqttClient.loop();
}

// ==========================================
// SETUP
// ==========================================

void setup() {

    Serial.begin(115200);

    delay(1000);

    Serial.println();
    Serial.println("########################################");
    Serial.println("#           NETPULSE ESP32             #");
    Serial.println("#        NETWORK MONITORING            #");
    Serial.println("#             MQTT v1.0                #");
    Serial.println("########################################");

    connectToWiFi();

    mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
    mqttClient.setKeepAlive(60);
    mqttClient.setBufferSize(1024);

    monitorHardware();
    monitorWiFi();

    collectNetworkMetrics();
    monitorInternetConnectivity();

    calculateNetworkHealthScore();

    connectToMQTT();
    publishAllMetrics();

    lastMonitoringTime = millis();
}

// ==========================================
// MAIN LOOP
// ==========================================

void loop() {

    checkWiFiConnection();

    connectToMQTT();

    mqttClient.loop();

    unsigned long currentTime = millis();

    if (currentTime - lastMonitoringTime >= MONITORING_INTERVAL) {

        lastMonitoringTime = currentTime;

        monitorHardware();

        monitorWiFi();

        collectNetworkMetrics();

        monitorInternetConnectivity();

        calculateNetworkHealthScore();

        publishAllMetrics();
    }

    delay(100);
}
