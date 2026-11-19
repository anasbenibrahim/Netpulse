#include <Arduino.h>
#include <WiFi.h>
#include <ESP32Ping.h>
#include <PubSubClient.h>
#include <Preferences.h>
#include <ArduinoJson.h>
#include <ESPmDNS.h>
#include <vector>
#include "secrets.h"

// Broker address, set in secrets.h. A name ending in ".local" is resolved with
// mDNS on every connection, so the ESP32 follows the PC when its IP or network changes.
#ifndef MQTT_BROKER_HOST
#define MQTT_BROKER_HOST "netpulse.local"
#endif

// ==========================================
// NETPULSE CONFIGURATION
// ==========================================

const unsigned long MONITORING_INTERVAL = 5000;
unsigned long lastMonitoringTime = 0;

// ==========================================
// MQTT CONFIGURATION
// ==========================================

const char* MQTT_BROKER = MQTT_BROKER_HOST;
const int MQTT_PORT = 1883;
const uint32_t MDNS_QUERY_TIMEOUT = 2000;

// Resolved broker address; cleared after a failed connection so the next attempt resolves again
IPAddress brokerIp;
bool brokerResolved = false;

const char* MQTT_CLIENT_ID = "netpulse-esp32-01";

const char* MQTT_TELEMETRY_TOPIC =
    "netpulse/devices/esp32-01/telemetry";

const char* MQTT_NETWORK_TOPIC =
    "netpulse/devices/esp32-01/network";

const char* MQTT_STATUS_TOPIC =
    "netpulse/devices/esp32-01/status";

// Commands from the dashboard (Wi-Fi scan, Wi-Fi switch) and their results
const char* MQTT_COMMAND_TOPIC =
    "netpulse/devices/esp32-01/command";

const char* MQTT_COMMAND_RESULT_TOPIC =
    "netpulse/devices/esp32-01/command-result";

WiFiClient wifiClient;
PubSubClient mqttClient(wifiClient);

unsigned long lastMqttReconnectAttempt = 0;
const unsigned long MQTT_RECONNECT_INTERVAL = 5000;

// ==========================================
// WIFI CREDENTIALS (saved in flash)
// ==========================================
// The network chosen from the dashboard is saved in flash (Preferences / NVS)
// and survives reboots. secrets.h only provides the default network, used on
// first boot and as a fallback when the saved network is not available.

Preferences preferences;

String wifiSsid = WIFI_SSID;
String wifiPassword = WIFI_PASSWORD;

const unsigned long WIFI_CONNECT_TIMEOUT = 15000;
const unsigned long MQTT_CONNECT_TIMEOUT = 15000;

// ==========================================
// REMOTE COMMANDS STATE
// ==========================================
// The MQTT callback only records the command; it is executed from loop(),
// because a Wi-Fi switch disconnects the MQTT client that runs the callback.

enum PendingAction { ACTION_NONE, ACTION_SCAN, ACTION_CONNECT_WIFI };

PendingAction pendingAction = ACTION_NONE;
String pendingRequestId = "";
String pendingSsid = "";
String pendingPassword = "";

// Result waiting to be published once MQTT is connected again (after a Wi-Fi switch)
String pendingResult = "";

void flushPendingResult() {

    if (pendingResult.length() == 0 || !mqttClient.connected()) {
        return;
    }

    if (mqttClient.publish(MQTT_COMMAND_RESULT_TOPIC, pendingResult.c_str())) {

        Serial.println("[COMMAND] Result published");
        pendingResult = "";
    }
}

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

bool tryConnectWiFi(const String& ssid, const String& password, unsigned long timeoutMs) {

    Serial.print("[WIFI] Connecting to ");
    Serial.println(ssid);

    WiFi.disconnect();
    delay(100);
    WiFi.begin(ssid.c_str(), password.c_str());

    unsigned long start = millis();

    while (WiFi.status() != WL_CONNECTED && millis() - start < timeoutMs) {

        delay(500);
        Serial.print(".");
    }

    Serial.println();

    return WiFi.status() == WL_CONNECTED;
}

void loadWiFiCredentials() {

    preferences.begin("netpulse", true);
    wifiSsid = preferences.getString("ssid", WIFI_SSID);
    wifiPassword = preferences.getString("pass", WIFI_PASSWORD);
    preferences.end();
}

void saveWiFiCredentials(const String& ssid, const String& password) {

    preferences.begin("netpulse", false);
    preferences.putString("ssid", ssid);
    preferences.putString("pass", password);
    preferences.end();
}

void connectToWiFi() {

    Serial.println();
    Serial.println("========== NETPULSE WIFI CONNECTION ==========");

    WiFi.mode(WIFI_STA);

    // NetPulse stores the credentials itself (Preferences), not the Wi-Fi driver
    WiFi.persistent(false);

    loadWiFiCredentials();

    bool connected = tryConnectWiFi(wifiSsid, wifiPassword, WIFI_CONNECT_TIMEOUT);

    if (!connected && wifiSsid != WIFI_SSID) {

        Serial.println("[WIFI] Saved network unavailable, falling back to the default network");

        connected = tryConnectWiFi(WIFI_SSID, WIFI_PASSWORD, WIFI_CONNECT_TIMEOUT);

        if (connected) {
            wifiSsid = WIFI_SSID;
            wifiPassword = WIFI_PASSWORD;
        }
    }

    if (connected) {

        Serial.println("[SUCCESS] WiFi connected!");

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

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
            WiFi.begin(wifiSsid.c_str(), wifiPassword.c_str());
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

bool connectMqttNow();

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

    connectMqttNow();
}

// Points the MQTT client at the broker: plain IP or DNS name as is, ".local" name through mDNS
bool resolveBroker() {

    String host = MQTT_BROKER;

    if (!host.endsWith(".local")) {
        mqttClient.setServer(MQTT_BROKER, MQTT_PORT);
        return true;
    }

    if (brokerResolved) {
        return true;
    }

    String name = host.substring(0, host.length() - 6);
    IPAddress ip = MDNS.queryHost(name.c_str(), MDNS_QUERY_TIMEOUT);

    if (ip == IPAddress(0, 0, 0, 0)) {

        Serial.print("[MQTT] Cannot resolve ");
        Serial.print(host);
        Serial.println(" (is the PC on the same network?)");
        return false;
    }

    brokerIp = ip;
    brokerResolved = true;
    mqttClient.setServer(brokerIp, MQTT_PORT);

    Serial.print("[MQTT] ");
    Serial.print(host);
    Serial.print(" resolved to ");
    Serial.println(brokerIp);

    return true;
}

bool connectMqttNow() {

    Serial.println();
    Serial.print("[MQTT] Connecting to broker ");
    Serial.print(MQTT_BROKER);
    Serial.println("...");

    if (!resolveBroker()) {
        return false;
    }

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

        // Clean session: the subscription must be renewed on every connection
        mqttClient.subscribe(MQTT_COMMAND_TOPIC, 1);

        flushPendingResult();

    } else {

        Serial.print("[MQTT] Connection failed. State: ");
        Serial.println(mqttClient.state());

        // The PC may have a new IP: resolve again next time
        brokerResolved = false;
    }

    return connected;
}

bool connectMqttWithin(unsigned long timeoutMs) {

    unsigned long start = millis();

    while (millis() - start < timeoutMs) {

        if (connectMqttNow()) {
            return true;
        }

        delay(1000);
    }

    return false;
}

// ==========================================
// MQTT JSON HELPERS
// ==========================================

String jsonString(const String& value) {

    JsonDocument doc;
    doc.set(value);

    String out;
    serializeJson(doc, out);

    return out;
}

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

    // Any Wi-Fi name can now be joined from the dashboard, so it must be JSON-escaped
    payload += "\"ssid\":";
    payload += jsonString(WiFi.status() == WL_CONNECTED ? WiFi.SSID() : "");
    payload += ",";

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
// REMOTE COMMANDS
// ==========================================

String buildResult(const String& requestId, const String& action, bool success, const String& message) {

    JsonDocument doc;

    doc["requestId"] = requestId;
    doc["action"] = action;
    doc["status"] = success ? "SUCCEEDED" : "FAILED";
    doc["message"] = message;

    if (success && WiFi.status() == WL_CONNECTED) {
        doc["ssid"] = WiFi.SSID();
        doc["ip"] = WiFi.localIP().toString();
    }

    String out;
    serializeJson(doc, out);

    return out;
}

void publishResult(const String& json) {

    pendingResult = json;
    flushPendingResult();
}

const char* securityName(wifi_auth_mode_t mode) {

    switch (mode) {
        case WIFI_AUTH_OPEN: return "OPEN";
        case WIFI_AUTH_WEP: return "WEP";
        case WIFI_AUTH_WPA_PSK: return "WPA";
        case WIFI_AUTH_WPA2_PSK: return "WPA2";
        case WIFI_AUTH_WPA_WPA2_PSK: return "WPA/WPA2";
        case WIFI_AUTH_WPA2_ENTERPRISE: return "WPA2-Enterprise";
        case WIFI_AUTH_WPA3_PSK: return "WPA3";
        case WIFI_AUTH_WPA2_WPA3_PSK: return "WPA2/WPA3";
        default: return "OTHER";
    }
}

void handleWiFiScan(const String& requestId) {

    const size_t MAX_NETWORKS = 20;

    Serial.println("[COMMAND] Scanning Wi-Fi networks...");

    int count = WiFi.scanNetworks();

    JsonDocument doc;
    doc["requestId"] = requestId;
    doc["action"] = "scan";

    if (count < 0) {

        doc["status"] = "FAILED";
        doc["message"] = "Wi-Fi scan failed";

    } else {

        JsonArray networks = doc["networks"].to<JsonArray>();
        std::vector<bool> used(count, false);

        // Strongest first, one entry per SSID, hidden networks skipped
        while (networks.size() < MAX_NETWORKS) {

            int best = -1;

            for (int i = 0; i < count; i++) {
                if (!used[i] && (best < 0 || WiFi.RSSI(i) > WiFi.RSSI(best))) {
                    best = i;
                }
            }

            if (best < 0) {
                break;
            }

            used[best] = true;

            String ssid = WiFi.SSID(best);

            if (ssid.length() == 0) {
                continue;
            }

            bool duplicate = false;

            for (JsonObject existing : networks) {
                if (ssid == existing["ssid"].as<const char*>()) {
                    duplicate = true;
                    break;
                }
            }

            if (duplicate) {
                continue;
            }

            JsonObject network = networks.add<JsonObject>();
            network["ssid"] = ssid;
            network["rssi"] = WiFi.RSSI(best);
            network["channel"] = WiFi.channel(best);
            network["security"] = securityName(WiFi.encryptionType(best));
        }

        doc["status"] = "SUCCEEDED";
        doc["message"] = String(networks.size()) + " networks found";
    }

    WiFi.scanDelete();

    String out;
    serializeJson(doc, out);

    Serial.print("[COMMAND] Scan done: ");
    Serial.println(doc["message"].as<const char*>());

    publishResult(out);
}

// Joins the new network and keeps it only if the MQTT broker is reachable from it.
// Otherwise it goes back to the previous network, so the device never becomes unreachable.
void handleWiFiSwitch(const String& requestId, const String& newSsid, const String& newPassword) {

    String previousSsid = wifiSsid;
    String previousPassword = wifiPassword;

    Serial.print("[COMMAND] Switching Wi-Fi to ");
    Serial.println(newSsid);

    // Tells the backend that the coming silence is intentional
    mqttClient.publish(MQTT_STATUS_TOPIC, "switching", true);
    mqttClient.disconnect();

    // The broker IP found on the old network may be wrong on the new one
    brokerResolved = false;

    String failure = "";

    if (!tryConnectWiFi(newSsid, newPassword, WIFI_CONNECT_TIMEOUT)) {

        failure = "Could not join " + newSsid + " (wrong password or out of range)";

    } else if (!connectMqttWithin(MQTT_CONNECT_TIMEOUT)) {

        failure = "Joined " + newSsid + " but the MQTT broker is unreachable from this network";
    }

    if (failure.length() == 0) {

        wifiSsid = newSsid;
        wifiPassword = newPassword;
        saveWiFiCredentials(newSsid, newPassword);

        Serial.println("[COMMAND] Wi-Fi switch succeeded, network saved");

        publishResult(buildResult(requestId, "connect_wifi", true, "Connected to " + newSsid));
        return;
    }

    Serial.print("[COMMAND] ");
    Serial.println(failure);
    Serial.println("[COMMAND] Rolling back to the previous network");

    mqttClient.disconnect();
    brokerResolved = false;

    // Sent as soon as MQTT is back (by connectMqttNow)
    pendingResult = buildResult(requestId, "connect_wifi", false, failure + "; back on " + previousSsid);

    if (tryConnectWiFi(previousSsid, previousPassword, WIFI_CONNECT_TIMEOUT)) {
        connectMqttWithin(MQTT_CONNECT_TIMEOUT);
    }
    // Otherwise checkWiFiConnection() keeps retrying the previous network from loop()
}

void onMqttMessage(char* topic, byte* payload, unsigned int length) {

    JsonDocument doc;
    DeserializationError error = deserializeJson(doc, payload, length);

    if (error) {

        Serial.print("[COMMAND] Invalid JSON: ");
        Serial.println(error.c_str());
        return;
    }

    String id = doc["id"] | "";
    String action = doc["action"] | "";

    // Never print the password
    Serial.print("[COMMAND] Received: ");
    Serial.println(action);

    if (pendingAction != ACTION_NONE) {

        publishResult(buildResult(id, action, false, "Device busy with another command"));
        return;
    }

    if (action == "scan") {

        pendingAction = ACTION_SCAN;

    } else if (action == "connect_wifi") {

        pendingSsid = doc["ssid"] | "";
        pendingPassword = doc["password"] | "";

        if (pendingSsid.length() == 0) {

            publishResult(buildResult(id, action, false, "Missing ssid"));
            return;
        }

        pendingAction = ACTION_CONNECT_WIFI;

    } else {

        publishResult(buildResult(id, action, false, "Unknown action: " + action));
        return;
    }

    pendingRequestId = id;
}

void processPendingCommand() {

    if (pendingAction == ACTION_NONE) {
        return;
    }

    PendingAction action = pendingAction;
    String requestId = pendingRequestId;
    String ssid = pendingSsid;
    String password = pendingPassword;

    pendingSsid = "";
    pendingPassword = "";

    if (action == ACTION_SCAN) {
        handleWiFiScan(requestId);
    } else {
        handleWiFiSwitch(requestId, ssid, password);
    }

    pendingAction = ACTION_NONE;
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
    Serial.println("#             MQTT v1.1                #");
    Serial.println("########################################");

    connectToWiFi();

    // mDNS responder, needed to resolve a ".local" broker name
    if (!MDNS.begin("netpulse-esp32-01")) {
        Serial.println("[MDNS] Failed to start");
    }

    mqttClient.setKeepAlive(60);

    // Bound each broker attempt (TCP connect 3 s, CONNACK 5 s instead of 15 s) so that a
    // Wi-Fi switch and its rollback always finish well within the backend command timeout,
    // even on a network that accepts the connection but never answers (captive portal)
    wifiClient.setConnectionTimeout(3000);
    mqttClient.setSocketTimeout(5);

    // Large enough for a Wi-Fi scan result (up to 20 networks)
    mqttClient.setBufferSize(4096);

    mqttClient.setCallback(onMqttMessage);

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

    processPendingCommand();

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
