-- Migration V8: Add ML fraud detection rule
-- Description: Adds production ML rule with production-15f model configuration

-- Insert ML fraud detection rule (priority 0 - checked first, highest priority)
INSERT INTO rules (name, rule_type, priority, enabled, params_json, version, created_by, updated_by) VALUES
('ML Fraud Detection (production-15f)', 'ML', 0, true,
 '{
   "threshold": 0.5,
   "modelVersion": "production-15f",
   "fallbackAction": "PASS"
 }',
 1, 'system', 'system');

-- Update comment on params_json column to document ML rule parameters
COMMENT ON COLUMN rules.params_json IS 'Rule-specific parameters in JSON format. ML rules support: threshold (0.0-1.0), modelVersion (string), fallbackAction (PASS|BLOCK for error handling)';
