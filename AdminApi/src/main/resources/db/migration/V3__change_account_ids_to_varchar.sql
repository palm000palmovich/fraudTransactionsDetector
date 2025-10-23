-- Migration V3: Change source_id and destination_id from BIGINT to VARCHAR
-- Reason: Support alphanumeric account identifiers (e.g., ACC877572, CARD-12345)
-- Date: 2025-10-22

-- Drop existing index on source_id (will be recreated with new type)
DROP INDEX IF EXISTS idx_transactions_source_id;

-- Change column types from BIGINT to VARCHAR(100)
-- USING clause converts existing numeric values to strings
ALTER TABLE transactions
    ALTER COLUMN source_id TYPE VARCHAR(100) USING source_id::VARCHAR;

ALTER TABLE transactions
    ALTER COLUMN destination_id TYPE VARCHAR(100) USING destination_id::VARCHAR;

-- Recreate index for source_id with new VARCHAR type
CREATE INDEX idx_transactions_source_id ON transactions(source_id);

-- Add index for destination_id for faster lookups
CREATE INDEX idx_transactions_destination_id ON transactions(destination_id);

-- Update column comments
COMMENT ON COLUMN transactions.source_id IS 'Source account identifier (alphanumeric, e.g., ACC877572 or 100001)';
COMMENT ON COLUMN transactions.destination_id IS 'Destination account identifier (alphanumeric, e.g., ACC877572 or 200002)';
