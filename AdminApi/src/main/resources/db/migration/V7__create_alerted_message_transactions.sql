-- Migration V7: Create alerted transactions table
--Description: Saving alert-messages with incorrect transactions


-- Flyway migration script for alert_messages table
CREATE TABLE alert_messages (
    id BIGSERIAL PRIMARY KEY,
    rule_id BIGINT NOT NULL,
    rule_name VARCHAR(255) NOT NULL,
    reason TEXT NOT NULL,
    sending_time TIMESTAMP WITH TIME ZONE NOT NULL,
    correlation_id UUID NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Индексы для быстрого поиска
CREATE INDEX idx_alert_messages_correlation_id ON alert_messages(correlation_id);
CREATE INDEX idx_alert_messages_rule_id ON alert_messages(rule_id);
CREATE INDEX idx_alert_messages_sending_time ON alert_messages(sending_time);