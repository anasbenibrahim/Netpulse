#ifndef SECRETS_H
#define SECRETS_H

// ==========================================
// NETPULSE - DEFAULT WIFI
// ==========================================
// Copy this file to secrets.h (git-ignored) and fill in your network.
// This is only the default network: once running, the ESP32 can be moved to
// another Wi-Fi from the dashboard (page Wi-Fi), and that choice is kept in flash.

#define WIFI_SSID "your-wifi-name"
#define WIFI_PASSWORD "your-wifi-password"

// ==========================================
// NETPULSE - MQTT BROKER
// ==========================================
// Machine running Mosquitto. Prefer its mDNS name: "<hostname>.local"
// (run `hostname` on that machine). The ESP32 resolves it on every connection,
// so nothing has to change when the PC gets a new IP or joins another network.
// A plain IP ("192.168.1.10") also works but must be updated when it changes.

#define MQTT_BROKER_HOST "your-pc-hostname.local"

#endif
