package space.maatini.eventsourcing.entity;

import io.smallrye.mutiny.Uni;

/**
 * Marker interface for all aggregate roots in the event sourcing system.
 * Verbessert für stabiles Replay.
 */
public interface AggregateRoot {

    /**
     * Deletes all instances of this aggregate.
     * Used for full replays (now works with PanacheEntityBase).
     */
    static Uni<Long> deleteAll() {
        throw new UnsupportedOperationException("Concrete aggregates inherit deleteAll() from PanacheEntityBase");
    }

    /**
     * Returns the JPQL entity name (usually the simple class name).
     */
    static String getEntityName() {
        throw new UnsupportedOperationException("Override or use getSimpleName() in ProjectionService");
    }
}
