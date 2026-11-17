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
// NETWORK DIAGNOSTICS - PACKET LOSS
// ==========================================

void collectNetworkMetrics() {

    Serial.println();
    Serial.println("========== NETPULSE NETWORK DIAGNOSTICS ==========");

    if (WiFi.status() != WL_CONNECTED) {

        Serial.println("Network Status: WIFI DISCONNECTED");
        Serial.println("Gateway Latency: N/A");
        Serial.println("Packet Loss: N/A");

        Serial.println("==================================================");

        return;
    }

    IPAddress gateway = WiFi.gatewayIP();

    Serial.print("Gateway: ");
    Serial.println(gateway);

    Serial.println("[INFO] Starting network diagnostics...");

    const int PING_COUNT = 10;

    int packetsSent = 0;
    int packetsReceived = 0;
    int packetsLost = 0;

    float totalLatency = 0.0;

    // Send individual ping requests

    for (int i = 0; i < PING_COUNT; i++) {

        packetsSent++;

        Serial.print("[PING] Request ");
        Serial.print(i + 1);
        Serial.print("/");
        Serial.println(PING_COUNT);

        bool success = Ping.ping(gateway, 1);

        if (success) {

            packetsReceived++;

            float latency = Ping.averageTime();

            totalLatency += latency;

            Serial.print("[SUCCESS] Response time: ");
            Serial.print(latency, 2);
            Serial.println(" ms");

        } else {

            packetsLost++;

            Serial.println("[ERROR] Request timeout");
        }

        delay(500);
    }

    // Calculate packet loss

    float packetLoss = 0.0;

    if (packetsSent > 0) {

        packetLoss =
            100.0f * packetsLost / packetsSent;
    }

    // Calculate average latency

    Serial.println();
    Serial.println("---------- RESULTS ----------");

    if (packetsReceived > 0) {

        float averageLatency =
            totalLatency / packetsReceived;

        Serial.print("Gateway Latency: ");
        Serial.print(averageLatency, 2);
        Serial.println(" ms");

        Serial.println("Gateway Status: REACHABLE");

    } else {

        Serial.println("Gateway Latency: N/A");

        Serial.println("Gateway Status: UNREACHABLE");
    }

    Serial.println();

    Serial.print("Packets Sent: ");
    Serial.println(packetsSent);

    Serial.print("Packets Received: ");
    Serial.println(packetsReceived);

    Serial.print("Packets Lost: ");
    Serial.println(packetsLost);

    Serial.print("Packet Loss: ");
    Serial.print(packetLoss, 2);
    Serial.println(" %");

    // Network quality classification

    Serial.println();

    if (packetLoss == 0) {

        Serial.println("Network Quality: EXCELLENT");

    } else if (packetLoss <= 5) {

        Serial.println("Network Quality: GOOD");

    } else if (packetLoss <= 20) {

        Serial.println("Network Quality: FAIR");

    } else {

        Serial.println("Network Quality: POOR");
    }

    Serial.println("==============================");
    Serial.println("==================================================");
}


// ==========================================
// WIFI CONNECTION
// ==========================================

void connectToWiFi() {

    Serial.println();
    Serial.println("==========================================");
    Serial.println("        NETPULSE - WIFI CONNECTION");
    Serial.println("==========================================");

    Serial.print("Connecting to WiFi: ");
    Serial.println(WIFI_SSID);

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

    if (WiFi.status() != WL_CONNECTED) {

        Serial.println("[WARNING] WiFi disconnected!");
        Serial.println("[INFO] Reconnecting...");

        WiFi.disconnect();
        WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

        int attempts = 0;

        while (WiFi.status() != WL_CONNECTED && attempts < 20) {

            delay(500);
            Serial.print(".");

            attempts++;
        }

        Serial.println();

        if (WiFi.status() == WL_CONNECTED) {

            Serial.println("[SUCCESS] WiFi reconnected!");

            Serial.print("IP Address: ");
            Serial.println(WiFi.localIP());

        } else {

            Serial.println("[ERROR] Reconnection failed!");
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

        Serial.print("RSSI: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");

        Serial.println("========================================");

    } else {

        Serial.println("[WARNING] WiFi is not connected!");
    }
}


// ==========================================
// GATEWAY PING
// ==========================================

void monitorGatewayPing() {

    if (WiFi.status() != WL_CONNECTED) {

        Serial.println("[PING] WiFi unavailable. Ping skipped.");
        return;
    }

    IPAddress gateway = WiFi.gatewayIP();

    Serial.println();
    Serial.println("========== GATEWAY PING ================");

    Serial.print("Gateway IP: ");
    Serial.println(gateway);

    bool reachable = Ping.ping(gateway, 5);

    if (reachable) {

        float averageLatency = Ping.averageTime();

        Serial.println("Status: REACHABLE");

        Serial.print("Average Latency: ");
        Serial.print(averageLatency);
        Serial.println(" ms");

    } else {

        Serial.println("Status: UNREACHABLE");
        Serial.println("Packet Loss: 100%");
    }

    Serial.println("========================================");
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
    Serial.println("########################################");

    connectToWiFi();

    monitorHardware();

    monitorWiFi();

    monitorGatewayPing();

    collectNetworkMetrics();

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

        monitorGatewayPing();

        collectNetworkMetrics();
    }

    delay(100);
}