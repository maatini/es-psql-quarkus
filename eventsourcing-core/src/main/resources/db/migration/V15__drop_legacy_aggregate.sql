-- Drop the legacy specific aggregate table from Stage 1. 
-- The framework uses a generic JSON-based read model (Stage 2: aggregate_state) instead.
DROP TABLE IF EXISTS vertreter_aggregate CASCADE;
DROP SEQUENCE IF EXISTS vertreter_aggregate_seq CASCADE;
