-- Enable Reactive Event Notifications

-- 1. Create function to notify 'events_channel'
CREATE OR REPLACE FUNCTION notify_event() RETURNS TRIGGER AS $$
BEGIN
    -- Payload is the event ID (useful if we want to fetch specific event, 
    -- but usually we just wake up the processor)
    PERFORM pg_notify('events_channel', NEW.id::text);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

-- 2. Create trigger on events table
DROP TRIGGER IF EXISTS trg_notify_event ON events;

CREATE TRIGGER trg_notify_event
    AFTER INSERT ON events
    FOR EACH ROW
    EXECUTE FUNCTION notify_event();
