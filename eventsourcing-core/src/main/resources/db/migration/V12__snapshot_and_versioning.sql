CREATE TABLE aggregate_snapshots (
    aggregate_id VARCHAR(255) PRIMARY KEY,
    aggregate_type VARCHAR(255) NOT NULL,
    aggregate_version INTEGER NOT NULL DEFAULT 0,
    last_event_id UUID NOT NULL,
    state JSONB NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_snapshots_type ON aggregate_snapshots(aggregate_type);

ALTER TABLE events ADD COLUMN data_version INTEGER NOT NULL DEFAULT 1;
