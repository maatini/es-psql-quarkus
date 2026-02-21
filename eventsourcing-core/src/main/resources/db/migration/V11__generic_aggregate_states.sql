-- Generisches Read-Model für alle Aggregate (Stufe 2)
CREATE TABLE IF NOT EXISTS aggregate_states (
    id          TEXT NOT NULL,
    type        TEXT NOT NULL,                    -- z.B. 'vertreter', 'abwesenheit'
    state       JSONB NOT NULL DEFAULT '{}',
    version     INTEGER NOT NULL DEFAULT 0,
    last_event_id UUID,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY (type, id)
);

-- Optimierungen
CREATE INDEX idx_aggregate_states_type_id ON aggregate_states(type, id);
CREATE INDEX idx_aggregate_states_type ON aggregate_states(type);
CREATE INDEX idx_aggregate_states_updated_at ON aggregate_states(updated_at);

-- GIN-Index für JSON-Suchen (sehr wichtig!)
CREATE INDEX idx_aggregate_states_state_gin ON aggregate_states USING GIN (state);

-- Beispiel: Schnelle Suche nach E-Mail (kann später als View materialisiert werden)
CREATE INDEX idx_aggregate_states_email ON aggregate_states ((state ->> 'email')) 
    WHERE type = 'vertreter';
