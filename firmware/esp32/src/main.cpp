#include <Arduino.h>
#include <WiFi.h>
#include <ESP32Ping.h>
#include "secrets.h"

// ==========================================
// NETPULSE - CONFIGURATION
// ==========================================

const unsigned long MONITORING_INTERVAL = 5000;

unsigned long lastMonitoringTime = 0;


// ==========================================
// NETWORK HEALTH - GLOBAL METRICS
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


// ==========================================
// WIFI CONNECTION
// ==========================================

void connectToWiFi() {

    Serial.println();
    Serial.println("==========================================");
    Serial.println("        NETPULSE - WIFI CONNECTION");
    Serial.println("==========================================");

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

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("Gateway: ");
        Serial.println(WiFi.gatewayIP());

        Serial.print("Subnet Mask: ");
        Serial.println(WiFi.subnetMask());

        Serial.print("Signal Strength: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");

    } else {

        Serial.println("[ERROR] WiFi connection failed!");
    }

    Serial.println("==========================================");
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
            Serial.println("[INFO] Reconnecting...");

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
    Serial.print(freeHeap);
    Serial.println(" bytes");

    Serial.print("Total Heap: ");
    Serial.print(totalHeap);
    Serial.println(" bytes");

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

        Serial.println();
        Serial.println("========== WIFI TELEMETRY ==============");

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("Gateway: ");
        Serial.println(WiFi.gatewayIP());

        Serial.print("Subnet Mask: ");
        Serial.println(WiFi.subnetMask());

        currentRSSI = WiFi.RSSI();

        Serial.print("RSSI: ");
        Serial.print(currentRSSI);
        Serial.println(" dBm");

        Serial.println("========================================");

    } else {

        currentRSSI = -100;

        Serial.println("[WARNING] WiFi is not connected!");
    }
}


// ==========================================
// NETWORK DIAGNOSTICS - GATEWAY
// ==========================================

void collectNetworkMetrics() {

    Serial.println();
    Serial.println("========== NETPULSE NETWORK DIAGNOSTICS ==========");

    if (WiFi.status() != WL_CONNECTED) {

        Serial.println("Network Status: WIFI DISCONNECTED");
        Serial.println("Gateway Latency: N/A");
        Serial.println("Packet Loss: N/A");

        gatewayMetricsAvailable = false;
        gatewayLatencyAvailable = false;

        gatewayLatency = -1;
        gatewayPacketLoss = -1;

        Serial.println("==================================================");

        return;
    }

    IPAddress gateway = WiFi.gatewayIP();

    Serial.print("Gateway: ");
    Serial.println(gateway);

    const int PING_COUNT = 10;

    int packetsSent = 0;
    int packetsReceived = 0;
    int packetsLost = 0;

    float totalLatency = 0;

    for (int i = 0; i < PING_COUNT; i++) {

        packetsSent++;

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
        100.0f * packetsLost / packetsSent;

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

    Serial.print("Packets Sent: ");
    Serial.println(packetsSent);

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

        Serial.println("Gateway Status: REACHABLE");

    } else {

        Serial.println("Gateway Latency: N/A");
        Serial.println("Gateway Status: UNREACHABLE");
    }

    Serial.println("=====================================");
}


// ==========================================
// INTERNET CONNECTIVITY MONITORING
// ==========================================

void monitorInternetConnectivity() {

    Serial.println();
    Serial.println("========== NETPULSE INTERNET MONITORING ==========");

    if (WiFi.status() != WL_CONNECTED) {

        internetMetricsAvailable = false;
        internetLatencyAvailable = false;

        internetLatency = -1;
        internetPacketLoss = -1;

        Serial.println("WiFi Status: DISCONNECTED");
        Serial.println("Internet Status: UNAVAILABLE");

        Serial.println("==================================================");

        return;
    }

    IPAddress internetTarget(1, 1, 1, 1);

    Serial.print("Target IP: ");
    Serial.println(internetTarget);

    const int PING_COUNT = 5;

    int packetsSent = 0;
    int packetsReceived = 0;
    int packetsLost = 0;

    float totalLatency = 0;

    for (int i = 0; i < PING_COUNT; i++) {

        packetsSent++;

        Serial.print("[INTERNET PING] ");
        Serial.print(i + 1);
        Serial.print("/");
        Serial.println(PING_COUNT);

        bool success = Ping.ping(internetTarget, 1);

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
        100.0f * packetsLost / packetsSent;

    internetMetricsAvailable = true;

    Serial.println();
    Serial.println("---------- INTERNET RESULTS ----------");

    Serial.print("Packets Sent: ");
    Serial.println(packetsSent);

    Serial.print("Packets Received: ");
    Serial.println(packetsReceived);

    Serial.print("Packets Lost: ");
    Serial.println(packetsLost);

    Serial.print("Packet Loss: ");
    Serial.print(internetPacketLoss, 2);
    Serial.println(" %");

    if (packetsReceived > 0) {

        internetLatency = totalLatency / packetsReceived;

        internetLatencyAvailable = true;

        Serial.println("Internet Status: CONNECTED");

        Serial.print("Average Latency: ");
        Serial.print(internetLatency, 2);
        Serial.println(" ms");

    } else {

        internetLatency = -1;
        internetLatencyAvailable = false;

        Serial.println("Internet Status: UNAVAILABLE");
        Serial.println("Average Latency: N/A");
    }

    Serial.println("=====================================");
}


// ==========================================
// NETWORK HEALTH - RSSI SCORE
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


// ==========================================
// NETWORK HEALTH - LATENCY SCORE
// ==========================================

float calculateLatencyScore(float latency) {

    if (latency < 0) return 0;

    if (latency <= 5) return 100;
    if (latency <= 10) return 90;
    if (latency <= 20) return 75;
    if (latency <= 50) return 50;
    if (latency <= 100) return 25;

    return 0;
}


// ==========================================
// NETWORK HEALTH - PACKET LOSS SCORE
// ==========================================

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

        Serial.println("Network Health Score: 0/100");
        Serial.println("Network Status: CRITICAL");
        Serial.println("Reason: WiFi disconnected");

        Serial.println("=============================================");

        return;
    }

    float totalScore = 0;

    int totalWeight = 0;

    // --------------------------------------
    // WIFI RSSI - 20%
    // --------------------------------------

    float rssiScore = calculateRSSIScore(currentRSSI);

    totalScore += rssiScore * 20;
    totalWeight += 20;

    // --------------------------------------
    // GATEWAY PACKET LOSS - 25%
    // --------------------------------------

    if (gatewayMetricsAvailable) {

        float score = calculatePacketLossScore(
            gatewayPacketLoss
        );

        totalScore += score * 25;
        totalWeight += 25;
    }

    // --------------------------------------
    // GATEWAY LATENCY - 15%
    // --------------------------------------

    if (gatewayLatencyAvailable) {

        float score = calculateLatencyScore(
            gatewayLatency
        );

        totalScore += score * 15;
        totalWeight += 15;
    }

    // --------------------------------------
    // INTERNET PACKET LOSS - 25%
    // --------------------------------------

    if (internetMetricsAvailable) {

        float score = calculatePacketLossScore(
            internetPacketLoss
        );

        totalScore += score * 25;
        totalWeight += 25;
    }

    // --------------------------------------
    // INTERNET LATENCY - 15%
    // --------------------------------------

    if (internetLatencyAvailable) {

        float score = calculateLatencyScore(
            internetLatency
        );

        totalScore += score * 15;
        totalWeight += 15;
    }

    // --------------------------------------
    // FINAL SCORE
    // --------------------------------------

    if (totalWeight == 0) {

        Serial.println("Network Health Score: UNKNOWN");
        Serial.println("Network Status: UNKNOWN");

        Serial.println("No metrics available.");

        Serial.println("=============================================");

        return;
    }

    float healthScore = totalScore / totalWeight;

    Serial.println();

    Serial.print("WiFi RSSI: ");
    Serial.print(currentRSSI);
    Serial.println(" dBm");

    Serial.print("Gateway Latency: ");

    if (gatewayLatencyAvailable) {
        Serial.print(gatewayLatency, 2);
        Serial.println(" ms");
    } else {
        Serial.println("N/A");
    }

    Serial.print("Gateway Packet Loss: ");

    if (gatewayMetricsAvailable) {
        Serial.print(gatewayPacketLoss, 2);
        Serial.println(" %");
    } else {
        Serial.println("N/A");
    }

    Serial.print("Internet Latency: ");

    if (internetLatencyAvailable) {
        Serial.print(internetLatency, 2);
        Serial.println(" ms");
    } else {
        Serial.println("N/A");
    }

    Serial.print("Internet Packet Loss: ");

    if (internetMetricsAvailable) {
        Serial.print(internetPacketLoss, 2);
        Serial.println(" %");
    } else {
        Serial.println("N/A");
    }

    Serial.println();
    Serial.println("---------------------------------------------");

    Serial.print("NETWORK HEALTH SCORE: ");

    Serial.print(healthScore, 2);

    Serial.println("/100");

    Serial.println("---------------------------------------------");

    // --------------------------------------
    // NETWORK CLASSIFICATION
    // --------------------------------------

    if (healthScore >= 90) {

        Serial.println("NETWORK STATUS: EXCELLENT");

    } else if (healthScore >= 75) {

        Serial.println("NETWORK STATUS: GOOD");

    } else if (healthScore >= 60) {

        Serial.println("NETWORK STATUS: FAIR");

    } else if (healthScore >= 40) {

        Serial.println("NETWORK STATUS: POOR");

    } else {

        Serial.println("NETWORK STATUS: CRITICAL");
    }

    Serial.println("=============================================");
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
    Serial.println("#        HEALTH SCORE v1.0             #");
    Serial.println("########################################");

    connectToWiFi();

    monitorHardware();

    monitorWiFi();

    collectNetworkMetrics();

    monitorInternetConnectivity();

    calculateNetworkHealthScore();

    lastMonitoringTime = millis();
}


// ==========================================
// MAIN LOOP
// ==========================================

void loop() {

    checkWiFiConnection();

    unsigned long currentTime = millis();

    if (currentTime - lastMonitoringTime >= MONITORING_INTERVAL) {

        lastMonitoringTime = currentTime;

        monitorHardware();

        monitorWiFi();

        collectNetworkMetrics();

        monitorInternetConnectivity();

        calculateNetworkHealthScore();
    }

    delay(100);
}