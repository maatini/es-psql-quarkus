-- Initial Schema Setup
-- Consolidated migration for Async Java Projections architecture

-- 1. Events Table (CloudEvents + Processing Tracking)
CREATE TABLE IF NOT EXISTS events (
    -- Required CloudEvents attributes
    id UUID PRIMARY KEY,
    source VARCHAR(255) NOT NULL,
    specversion VARCHAR(10) NOT NULL DEFAULT '1.0',
    type VARCHAR(255) NOT NULL,
    
    -- Optional CloudEvents attributes
    subject VARCHAR(255),
    time TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    datacontenttype VARCHAR(100) DEFAULT 'application/json',
    dataschema VARCHAR(255),
    
    -- Data payload
    data JSONB,

    -- Event Processing Tracking (from V5)
    processed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Index for querying events by subject (aggregate ID)
CREATE INDEX IF NOT EXISTS idx_events_subject ON events(subject);

-- Partial index for finding unprocessed events (Processing Queue)
CREATE INDEX IF NOT EXISTS idx_events_unprocessed ON events(processed_at) WHERE processed_at IS NULL;


-- 2. Vertreter Aggregates Table (Read Model)
CREATE TABLE IF NOT EXISTS vertreter_aggregate (
    id VARCHAR(255) PRIMARY KEY,
    name VARCHAR(255),
    email VARCHAR(255),
    updated_at TIMESTAMPTZ,
    event_id UUID,          -- ID of the last applied event
    version BIGINT,         -- Version number/sequence

    -- Represented Person (from V4)
    vertretene_person_id VARCHAR(255),
    vertretene_person_name VARCHAR(255)
);

-- Unique index to ensure one aggregate per email (if needed), handling soft deletes or updates
-- Note: logic moved to Java, but uniqueness constraints might still be useful.
-- Keeping it flexible as per original design interpretation.

-- Index for querying by represented person (from V4)
CREATE INDEX IF NOT EXISTS idx_vertreter_vertretene_person 
ON vertreter_aggregate(vertretene_person_id);
