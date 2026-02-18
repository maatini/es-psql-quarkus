-- Add tracking columns for error handling and retries
ALTER TABLE events ADD COLUMN IF NOT EXISTS failed_at TIMESTAMPTZ;
ALTER TABLE events ADD COLUMN IF NOT EXISTS retry_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE events ADD COLUMN IF NOT EXISTS error_message TEXT;

-- Update index for the processing queue to reflect the dead-letter threshold (retry_count < 5)
DROP INDEX IF EXISTS idx_events_unprocessed;
CREATE INDEX idx_events_unprocessed ON events(created_at) 
WHERE processed_at IS NULL AND retry_count < 5;
