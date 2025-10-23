-- Migration V2: Create rules table
-- Description: Stores fraud detection rules with First-Match priority
-- Rules are sorted by priority (ASC) and checked in order until first match

CREATE TABLE rules (
    -- Primary Key
    id BIGSERIAL PRIMARY KEY,

    -- Rule description
    name VARCHAR(255) NOT NULL,
    rule_type VARCHAR(20) NOT NULL,        -- THRESHOLD, PATTERN, COMPOSITE, ML

    -- *** FIRST-MATCH: priority determines check order ***
    -- Lower priority = checked earlier (1, 2, 3...)
    -- Rule engine STOPS at first triggered rule
    priority INT NOT NULL,

    -- Active/Inactive flag
    enabled BOOLEAN DEFAULT TRUE,

    -- Rule parameters (JSON format)
    -- Examples:
    -- THRESHOLD: {"field":"amount","operator":">","value":100000}
    -- PATTERN:   {"timeWindow":"PT10M","transactionLimit":5,"maxAmount":500}
    -- COMPOSITE: {"logicalOperator":"AND","childRuleIds":[1,2]}
    -- ML:        {"threshold":0.75}
    params_json TEXT NOT NULL,

    -- Versioning
    version INT DEFAULT 1,

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255),
    updated_by VARCHAR(255)
);

-- Indexes
-- CRITICAL for First-Match: retrieve enabled rules sorted by priority
CREATE INDEX idx_rules_enabled_priority ON rules(enabled, priority ASC);
CREATE INDEX idx_rules_type ON rules(rule_type);

-- Comments for documentation
COMMENT ON TABLE rules IS 'Fraud detection rules with First-Match evaluation policy';
COMMENT ON COLUMN rules.priority IS 'Lower number = checked earlier in First-Match sequence';
COMMENT ON COLUMN rules.params_json IS 'Rule-specific parameters in JSON format';

-- Insert demo rules for testing
-- Rule 1: High amount threshold (priority 1 - checked first)
INSERT INTO rules (name, rule_type, priority, enabled, params_json, version, created_by) VALUES
('High Amount Transfer', 'THRESHOLD', 1, true,
 '{"field":"amount","operator":">","value":100000}',
 1, 'system');

-- Rule 2: Pattern detection - rapid small transfers (priority 2)
INSERT INTO rules (name, rule_type, priority, enabled, params_json, version, created_by) VALUES
('Rapid Small Transfers Pattern', 'PATTERN', 2, true,
 '{"timeWindow":"PT10M","transactionLimit":5,"maxAmount":500,"patternType":"rapid_small"}',
 1, 'system');

-- Rule 3: Night transfer (priority 3)
INSERT INTO rules (name, rule_type, priority, enabled, params_json, version, created_by) VALUES
('Night High Transfer', 'THRESHOLD', 3, false,
 '{"field":"amount","operator":">","value":50000}',
 1, 'system');
