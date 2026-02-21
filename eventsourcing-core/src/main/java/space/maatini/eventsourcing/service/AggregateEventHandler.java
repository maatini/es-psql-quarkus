package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.AggregateRoot;

/**
 * Generic interface for aggregate-specific event handlers.
 * 
 * @param <A> The type of the aggregate root.
 */
public interface AggregateEventHandler<A extends AggregateRoot> extends EventHandler {
}
