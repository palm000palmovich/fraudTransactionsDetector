-- Migration V5: Create transaction_logs table
-- Description: Table for storing transaction processing logs (API -> Kafka -> Rules -> Notification)

CREATE TABLE transaction_logs (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Correlation ID for linking to transaction
    correlation_id UUID NOT NULL,

    -- Log details
    level VARCHAR(10) NOT NULL,            -- INFO, WARN, ERROR
    component VARCHAR(50) NOT NULL,        -- API, KAFKA_PRODUCER, KAFKA_CONSUMER, RULES, NOTIFICATION
    message TEXT NOT NULL,                 -- Log message
    details TEXT,                          -- Additional details (JSON format)

    -- Timestamp
    timestamp TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Indexes for fast queries
CREATE INDEX idx_transaction_logs_correlation_id ON transaction_logs(correlation_id);
CREATE INDEX idx_transaction_logs_level ON transaction_logs(level);
CREATE INDEX idx_transaction_logs_component ON transaction_logs(component);
CREATE INDEX idx_transaction_logs_timestamp ON transaction_logs(timestamp DESC);
CREATE INDEX idx_transaction_logs_correlation_timestamp ON transaction_logs(correlation_id, timestamp DESC);

-- Comments for documentation
COMMENT ON TABLE transaction_logs IS 'Logs tracking the full lifecycle of transactions through the system';
COMMENT ON COLUMN transaction_logs.level IS 'Log level: INFO, WARN, ERROR';
COMMENT ON COLUMN transaction_logs.component IS 'System component: API, KAFKA_PRODUCER, KAFKA_CONSUMER, RULES, NOTIFICATION';
COMMENT ON COLUMN transaction_logs.correlation_id IS 'Links log entries to specific transaction';
