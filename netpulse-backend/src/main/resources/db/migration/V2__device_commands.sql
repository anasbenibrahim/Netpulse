-- Remote commands sent from the dashboard to a device over MQTT (Wi-Fi scan, Wi-Fi switch).
-- Wi-Fi passwords are never stored: they only travel inside the MQTT command.
CREATE TABLE device_commands (
    id             VARCHAR(36)              PRIMARY KEY,
    device_id      BIGINT                   NOT NULL REFERENCES devices (id) ON DELETE CASCADE,
    type           VARCHAR(20)              NOT NULL,
    status         VARCHAR(16)              NOT NULL,
    target_ssid    VARCHAR(64),
    result_message VARCHAR(512),
    result_payload VARCHAR(8192),
    created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
    completed_at   TIMESTAMP WITH TIME ZONE
);
CREATE INDEX idx_commands_device_time ON device_commands (device_id, created_at);
CREATE INDEX idx_commands_status ON device_commands (status, created_at);

-- Remember which Wi-Fi each network measurement was taken on, to compare networks.
ALTER TABLE network_metrics ADD COLUMN ssid VARCHAR(64);
