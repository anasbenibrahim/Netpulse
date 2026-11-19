#!/usr/bin/env python3
"""Fake ESP32 for testing NetPulse without the board.

Publishes telemetry and network metrics every few seconds and answers the
dashboard's Wi-Fi commands (scan, connect_wifi) like the real firmware.

Usage:
    python3 scripts/simulate-esp32.py            # healthy network
    python3 scripts/simulate-esp32.py --bad      # degraded network (raises anomalies)

Wi-Fi switch: any password works, except "wrongpass", which simulates a failed
connection and a rollback to the previous network.
Requires the netpulse-mosquitto container to be running.
"""
import argparse
import json
import random
import subprocess
import threading
import time

CONTAINER = "netpulse-mosquitto"
DEVICE = "esp32-01"
PREFIX = f"netpulse/devices/{DEVICE}"

NEARBY = [
    {"ssid": "Simu-WiFi", "rssi": -52, "channel": 6, "security": "WPA2"},
    {"ssid": "Bureau-5G-Ext", "rssi": -63, "channel": 11, "security": "WPA2"},
    {"ssid": "Guest", "rssi": -71, "channel": 1, "security": "OPEN"},
    {"ssid": "Voisin-Box", "rssi": -84, "channel": 3, "security": "WPA3"},
]

state = {"ssid": "Simu-WiFi", "ip": "192.168.1.50", "start": time.time(), "lock": threading.Lock()}


def publish(subtopic, payload, retain=False):
    msg = payload if isinstance(payload, str) else json.dumps(payload)
    cmd = ["docker", "exec", CONTAINER, "mosquitto_pub", "-q", "1", "-t", f"{PREFIX}/{subtopic}", "-m", msg]
    if retain:
        cmd.append("-r")
    subprocess.run(cmd, check=False)


def metrics(bad):
    j = random.uniform
    if bad:
        return dict(rssi=round(j(-95, -88)), gatewayLatency=round(j(300, 450)), gatewayPacketLoss=60.0,
                    internetLatency=round(j(1000, 1500)), internetPacketLoss=60.0, networkHealthScore=30.0,
                    networkStatus="CRITICAL")
    return dict(rssi=round(j(-60, -50)), gatewayLatency=round(j(3, 10)), gatewayPacketLoss=0.0,
                internetLatency=round(j(30, 60)), internetPacketLoss=0.0, networkHealthScore=round(j(90, 98)),
                networkStatus="EXCELLENT")


def telemetry_loop(bad, interval):
    while True:
        with state["lock"]:
            ssid, ip = state["ssid"], state["ip"]
        m = metrics(bad)
        free_heap = random.randint(170000, 190000)
        publish("telemetry", dict(deviceId=DEVICE, uptime=int(time.time() - state["start"]), freeHeap=free_heap,
                                  totalHeap=320000, heapUsage=round(100 - free_heap / 3200, 1), flashSizeMB=4,
                                  ssid=ssid, ip=ip, rssi=m["rssi"]))
        publish("network", dict(deviceId=DEVICE, **m))
        print(f"[DATA] {ssid} rssi={m['rssi']} score={m['networkHealthScore']}", flush=True)
        time.sleep(interval)


def result(request_id, action, ok, message, **extra):
    publish("command-result", dict(requestId=request_id, action=action,
                                   status="SUCCEEDED" if ok else "FAILED", message=message, **extra))


def handle(raw):
    try:
        cmd = json.loads(raw)
    except json.JSONDecodeError:
        print(f"[COMMAND] invalid JSON: {raw}", flush=True)
        return
    rid, action = cmd.get("id", ""), cmd.get("action", "")
    print(f"[COMMAND] received {action}", flush=True)  # never print the password

    if action == "scan":
        time.sleep(2)
        networks = [dict(n, rssi=n["rssi"] + random.randint(-3, 3)) for n in NEARBY]
        result(rid, action, True, f"{len(networks)} networks found", networks=networks)
    elif action == "connect_wifi":
        ssid = cmd.get("ssid", "")
        if not ssid:
            result(rid, action, False, "Missing ssid")
            return
        with state["lock"]:
            previous = state["ssid"]
        publish("status", "switching", retain=True)
        time.sleep(4)
        if cmd.get("password") == "wrongpass":
            result(rid, action, False, f"Could not connect to {ssid}; back on {previous}",
                   ssid=previous, ip=state["ip"])
        else:
            with state["lock"]:
                state["ssid"], state["ip"] = ssid, f"192.168.1.{random.randint(20, 200)}"
            result(rid, action, True, f"Connected to {ssid}", ssid=ssid, ip=state["ip"])
        publish("status", "online", retain=True)
    else:
        result(rid, action, False, f"Unknown action: {action}")


def command_loop():
    sub = subprocess.Popen(["docker", "exec", CONTAINER, "mosquitto_sub", "-q", "1", "-t", f"{PREFIX}/command"],
                           stdout=subprocess.PIPE, text=True)
    for line in sub.stdout:
        if line.strip():
            threading.Thread(target=handle, args=(line.strip(),), daemon=True).start()


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--bad", action="store_true", help="send degraded metrics to trigger anomalies")
    parser.add_argument("--interval", type=float, default=5, help="seconds between readings (default 5)")
    args = parser.parse_args()

    publish("status", "online", retain=True)
    threading.Thread(target=command_loop, daemon=True).start()
    print(f"Simulating {DEVICE} ({'degraded' if args.bad else 'healthy'} network). Ctrl+C to stop.", flush=True)
    try:
        telemetry_loop(args.bad, args.interval)
    except KeyboardInterrupt:
        publish("status", "offline", retain=True)
        print("\nStopped: device reported offline.")


if __name__ == "__main__":
    main()
