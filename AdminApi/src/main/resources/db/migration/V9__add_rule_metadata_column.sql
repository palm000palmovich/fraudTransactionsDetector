-- Migration V9: Add rule_metadata column for ML scores and other evaluation metadata
-- Description: Adds JSONB column to store metadata from rule evaluation (e.g., ML scores, thresholds, model versions)

-- Add rule_metadata column (JSONB for PostgreSQL)
ALTER TABLE transactions
ADD COLUMN rule_metadata jsonb;

-- Add comment
COMMENT ON COLUMN transactions.rule_metadata IS 'Rule evaluation metadata in JSON format (e.g., ML scores, thresholds, model versions). Example: {"ml_score": 0.9234, "ml_threshold": 0.5, "ml_model_version": "production-15f"}';

-- Create GIN index for faster JSON queries (optional, for future queries on metadata)
CREATE INDEX idx_transactions_rule_metadata ON transactions USING GIN (rule_metadata);
