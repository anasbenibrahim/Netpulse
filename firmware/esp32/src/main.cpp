#include <Arduino.h>

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

    Serial.println("[INFO] Hardware monitoring started");
}

// ------------------------------------------
// Main Loop
// ------------------------------------------

void loop() {

    unsigned long currentTime = millis();

    if (currentTime - lastMonitoringTime >= MONITORING_INTERVAL) {

        lastMonitoringTime = currentTime;

        collectMetrics();
    }
}