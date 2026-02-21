-- V9: Dead Letter Table for permanently failed events
CREATE TABLE IF NOT EXISTS events_dead_letter (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    event_id UUID NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    type VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    reason TEXT NOT NULL,
    error_message TEXT,
    retry_count INTEGER NOT NULL,
    moved_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_dead_letter_moved_at ON events_dead_letter(moved_at);
CREATE INDEX IF NOT EXISTS idx_dead_letter_event_id ON events_dead_letter(event_id);
