#include <Arduino.h>

// NetPulse - ESP32 Firmware
// Initial firmware initialization

void setup() {

    // Initialize serial communication
    Serial.begin(115200);

    delay(1000);

    Serial.println();
    Serial.println("================================");
    Serial.println("       NETPULSE PROJECT         ");
    Serial.println("================================");

    Serial.println("[INFO] ESP32 initialized");
    Serial.println("[INFO] Arduino framework started");

    Serial.println("[INFO] Firmware version: 1.0.0");

    Serial.println("================================");
}

void loop() {

    Serial.println("[NETPULSE] ESP32 is running...");

    delay(5000);
}