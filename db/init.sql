-- Initialize Event Store Schema
-- This script is used for Docker, Devbox, and K8s initialization.

CREATE TABLE IF NOT EXISTS events (
    id UUID PRIMARY KEY,
    source VARCHAR(255) NOT NULL,
    specversion VARCHAR(255) NOT NULL,
    type VARCHAR(255) NOT NULL,
    subject VARCHAR(255),
    time TIMESTAMPTZ NOT NULL,
    datacontenttype VARCHAR(255),
    dataschema VARCHAR(255),
    data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    processed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    retry_count INTEGER NOT NULL DEFAULT 0,
    error_message TEXT,
    data_version INTEGER NOT NULL DEFAULT 1,
    aggregate_version INTEGER NOT NULL
);

-- Ensure unique constraint for concurrency control
CREATE UNIQUE INDEX IF NOT EXISTS uk_events_concurrency ON events (subject, aggregate_version);

-- Function to notify the events_channel on new inserts
CREATE OR REPLACE FUNCTION notify_events() RETURNS TRIGGER AS $$
BEGIN
  PERFORM pg_notify('events_channel', NEW.id::text);
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- Trigger to execute the notify function
DROP TRIGGER IF EXISTS events_notify ON events;
CREATE TRIGGER events_notify 
  AFTER INSERT ON events 
  FOR EACH ROW EXECUTE FUNCTION notify_events();
