#include <Arduino.h>
#include <WiFi.h>

const char* WIFI_SSID = "Orange-4D9B";
const char* WIFI_PASSWORD = "MA8B41D5BGH";


void connectToWiFi() {

    Serial.println("\n========== WIFI CONNECTION ==========");

    Serial.print("[INFO] Connecting to: ");
    Serial.println(WIFI_SSID);

    WiFi.mode(WIFI_STA);

    WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

    unsigned long startAttempt = millis();

    while (WiFi.status() != WL_CONNECTED &&
           millis() - startAttempt < 20000) {

        Serial.print(".");
        delay(500);
    }

    if (WiFi.status() == WL_CONNECTED) {

        Serial.println("\n[SUCCESS] WiFi connected!");

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("Signal Strength: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");

    } else {

        Serial.println("\n[ERROR] WiFi connection failed!");
    }

    Serial.println("=====================================");
}


void collectWiFiMetrics() {

    Serial.println("\n========== NETPULSE WIFI TELEMETRY ==========");

    if (WiFi.status() == WL_CONNECTED) {

        Serial.println("WiFi Status: CONNECTED");

        Serial.print("SSID: ");
        Serial.println(WiFi.SSID());

        Serial.print("IP Address: ");
        Serial.println(WiFi.localIP());

        Serial.print("RSSI: ");
        Serial.print(WiFi.RSSI());
        Serial.println(" dBm");

        Serial.print("Gateway: ");
        Serial.println(WiFi.gatewayIP());

        Serial.print("Subnet Mask: ");
        Serial.println(WiFi.subnetMask());

    } else {

        Serial.println("WiFi Status: DISCONNECTED");
    }

    Serial.println("=============================================");
}



void checkWiFiConnection() {

    static unsigned long lastReconnectAttempt = 0;

    if (WiFi.status() != WL_CONNECTED) {

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
// NETPULSE - ESP32 HARDWARE MONITORING
// Firmware Version: 1.1.0
// ==========================================

unsigned long lastMonitoringTime = 0;

const unsigned long MONITORING_INTERVAL = 5000;

// ------------------------------------------
// Display ESP32 Hardware Information
// ------------------------------------------

void displayHardwareInfo() {

    Serial.println("\n========== NETPULSE HARDWARE ==========");

    Serial.print("Chip Model: ");
    Serial.println(ESP.getChipModel());

    Serial.print("Chip Revision: ");
    Serial.println(ESP.getChipRevision());

    Serial.print("CPU Frequency: ");
    Serial.print(ESP.getCpuFreqMHz());
    Serial.println(" MHz");

    Serial.print("CPU Cores: ");
    Serial.println(ESP.getChipCores());

    Serial.print("Flash Size: ");
    Serial.print(ESP.getFlashChipSize() / 1024);
    Serial.println(" KB");

    Serial.println("=======================================");
}

// ------------------------------------------
// Collect ESP32 Metrics
// ------------------------------------------

void collectMetrics() {

    unsigned long uptime = millis() / 1000;

    uint32_t freeHeap = ESP.getFreeHeap();

    uint32_t totalHeap = ESP.getHeapSize();

    Serial.println("\n========== NETPULSE TELEMETRY ==========");

    Serial.print("Uptime: ");
    Serial.print(uptime);
    Serial.println(" seconds");

    Serial.print("Free Heap: ");
    Serial.print(freeHeap);
    Serial.println(" bytes");

    Serial.print("Total Heap: ");
    Serial.print(totalHeap);
    Serial.println(" bytes");

    Serial.print("Heap Usage: ");

    float usage = 100.0f * (totalHeap - freeHeap) / totalHeap;

    Serial.print(usage, 2);
    Serial.println(" %");

    Serial.println("========================================");
}

// ------------------------------------------
// Setup
// ------------------------------------------

void setup() {

    Serial.begin(115200);

    delay(1000);

    Serial.println("\n");
    Serial.println("================================");
    Serial.println("       NETPULSE PROJECT         ");
    Serial.println("       Firmware v1.1.0          ");
    Serial.println("================================");

    Serial.println("[INFO] ESP32 initialized");

    displayHardwareInfo();
    connectToWiFi();

    Serial.println("[INFO] Hardware monitoring started");
}

// ------------------------------------------
// Main Loop
// ------------------------------------------

void loop() {

    unsigned long currentTime = millis();

    checkWiFiConnection();

    if (currentTime - lastMonitoringTime >= MONITORING_INTERVAL) {

        lastMonitoringTime = currentTime;

        collectMetrics();

        collectWiFiMetrics();
    }
}