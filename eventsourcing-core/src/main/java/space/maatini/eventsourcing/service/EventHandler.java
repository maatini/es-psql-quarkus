package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import io.smallrye.mutiny.Uni;

/**
 * Base interface for event handlers.
 */
public interface EventHandler {
    /**
     * Checks if this handler can process the given event type.
     */
    boolean canHandle(String eventType);

    /**
     * Processes the event.
     */
    Uni<Void> handle(CloudEvent event);
}
