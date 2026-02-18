package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.AggregateRoot;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Annotation to mark a handler and specify which event type prefix it handles
 * and which aggregate it belongs to.
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface HandlesEvents {
    /**
     * The event type prefix (e.g., "space.maatini.vertreter.").
     */
    String value();

    /**
     * The aggregate class this handler works on.
     */
    Class<? extends AggregateRoot> aggregate();
}
