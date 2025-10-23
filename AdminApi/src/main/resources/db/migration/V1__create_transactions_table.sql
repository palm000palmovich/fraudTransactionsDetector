-- Migration V1: Create transactions table
-- Description: Main table for storing all financial transactions
-- First-Match approach: saves only the FIRST triggered rule

CREATE TABLE transactions (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Correlation ID for tracing across system (logs, Kafka, notifications)
    correlation_id UUID UNIQUE NOT NULL,

    -- Transaction data
    source_id BIGINT NOT NULL,
    destination_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(10),
    timestamp TIMESTAMP NOT NULL,
    channel VARCHAR(50),
    geo VARCHAR(100),
    description TEXT,

    -- Processing status
    -- PENDING: just received, not yet processed by rule engine
    -- PROCESSED: checked by rules, no alerts
    -- ALERTED: at least one rule triggered
    -- REVIEWED: manually reviewed by analyst
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',

    -- *** FIRST-MATCH: only the FIRST triggered rule is saved ***
    triggered_rule_id BIGINT,              -- ID of the rule that triggered (NULL if no rules triggered)
    triggered_rule_name VARCHAR(255),      -- Rule name (for UI convenience)
    trigger_reason TEXT,                   -- Why the rule triggered (JSON or plain text)

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP                 -- When processed by rule engine
);

-- Indexes for fast queries
CREATE INDEX idx_transactions_correlation_id ON transactions(correlation_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_timestamp ON transactions(timestamp DESC);
CREATE INDEX idx_transactions_source_id ON transactions(source_id);
CREATE INDEX idx_transactions_triggered_rule ON transactions(triggered_rule_id);

-- Comments for documentation
COMMENT ON TABLE transactions IS 'Stores all financial transactions for fraud detection analysis';
COMMENT ON COLUMN transactions.status IS 'PENDING, PROCESSED, ALERTED, REVIEWED';
COMMENT ON COLUMN transactions.triggered_rule_id IS 'First-Match: ID of the first rule that triggered';
COMMENT ON COLUMN transactions.trigger_reason IS 'Explanation why the rule triggered (for UI display)';
