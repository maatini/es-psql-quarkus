-- Add aggregate_version to events table for optimistic concurrency control
ALTER TABLE events ADD COLUMN aggregate_version INT;

-- Populate existing events with a sequential version per subject
-- This uses a Window Function to assign 1, 2, 3... based on creation time
UPDATE events e
SET aggregate_version = sub.seq
FROM (
    SELECT id, row_number() OVER (PARTITION BY subject ORDER BY created_at ASC, id ASC) as seq
    FROM events
) sub
WHERE e.id = sub.id;

-- Make it non-nullable after population
ALTER TABLE events ALTER COLUMN aggregate_version SET NOT NULL;

-- CRITICAL: Add Unique constraint to prevent branching history
-- Two events for the same subject cannot have the same version
ALTER TABLE events ADD CONSTRAINT unique_subject_version UNIQUE (subject, aggregate_version);

-- Update snapshots table to also use this versioning if needed
-- (The snapshots table already has aggregate_version, so we're good there)
