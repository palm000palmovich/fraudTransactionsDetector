-- Migration V11: Create notification channels table
-- Description: Table for managing notification channels (Telegram, WebSocket, Webhook)

CREATE TABLE notification_channels (
    id BIGSERIAL PRIMARY KEY,
    channel_type VARCHAR(50) NOT NULL UNIQUE,
    enabled BOOLEAN DEFAULT TRUE NOT NULL,
    config_json TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Insert initial channels
INSERT INTO notification_channels (channel_type, enabled, config_json) VALUES
('TELEGRAM', true, '{"botToken": "7930102753:AAEjOv3YdgjDmfbgsJnKgVq1Xw5OtBIfOV0", "botUsername": "AlertBot"}'),
('WEBSOCKET', true, '{}'),
('WEBHOOK', false, '{"url": "http://localhost:8081/api/webhooks/test", "timeout": 5000}');

-- Index for fast lookup by channel type
CREATE INDEX idx_notification_channels_type ON notification_channels(channel_type);
CREATE INDEX idx_notification_channels_enabled ON notification_channels(enabled);
