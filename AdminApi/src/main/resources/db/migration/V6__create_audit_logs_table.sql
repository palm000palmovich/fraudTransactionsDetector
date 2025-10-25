-- Audit Logs Table for tracking CRUD operations on Rules
CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    timestamp TIMESTAMP NOT NULL DEFAULT NOW(),
    username VARCHAR(100) NOT NULL,
    action VARCHAR(20) NOT NULL, -- CREATE, UPDATE, DELETE, TOGGLE
    rule_id BIGINT,
    rule_name VARCHAR(255),
    details TEXT
);

-- Indexes for performance
CREATE INDEX idx_audit_logs_timestamp ON audit_logs(timestamp DESC);
CREATE INDEX idx_audit_logs_username ON audit_logs(username);
CREATE INDEX idx_audit_logs_action ON audit_logs(action);
CREATE INDEX idx_audit_logs_rule_id ON audit_logs(rule_id);

-- Comments
COMMENT ON TABLE audit_logs IS 'Audit trail for rule management operations';
COMMENT ON COLUMN audit_logs.timestamp IS 'When the action was performed';
COMMENT ON COLUMN audit_logs.username IS 'User who performed the action';
COMMENT ON COLUMN audit_logs.action IS 'Type of action: CREATE, UPDATE, DELETE, TOGGLE';
COMMENT ON COLUMN audit_logs.rule_id IS 'ID of the affected rule';
COMMENT ON COLUMN audit_logs.rule_name IS 'Name of the rule at the time of action';
COMMENT ON COLUMN audit_logs.details IS 'Additional details about the action (JSON or text)';
