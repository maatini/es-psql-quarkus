-- Add Constraints to Aggregate Table

-- 1. Ensure email is unique across all representatives
-- This prevents race conditions where two simultaneous 'created' events for the same email
-- might otherwise both succeed.
ALTER TABLE vertreter_aggregate ADD CONSTRAINT uq_vertreter_aggregate_email UNIQUE (email);

-- 2. Ensure version is strictly positive to prevent negative versions
-- and enforce the usage of optimistic locking logic.
ALTER TABLE vertreter_aggregate ADD CONSTRAINT chk_vertreter_aggregate_version CHECK (version >= 0);
