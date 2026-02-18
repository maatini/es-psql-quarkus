package space.maatini.eventsourcing.entity;

import io.smallrye.mutiny.Uni;

/**
 * Marker interface for all aggregate roots in the event sourcing system.
 */
public interface AggregateRoot {
    /**
     * Deletes all instances of this aggregate.
     * Used for full replays.
     */
    static Uni<Long> deleteAll() {
        throw new UnsupportedOperationException("Subclasses must implement static deleteAll()");
    }
}
