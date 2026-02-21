package space.maatini.eventsourcing.command;

import io.smallrye.mutiny.Uni;
import space.maatini.eventsourcing.domain.DomainAggregateRoot;

/**
 * Interface for processing a specific command.
 *
 * @param <A> The Aggregate Root type the command operates on.
 * @param <C> The Command type (usually a DTO or record).
 */
public interface CommandHandler<A extends DomainAggregateRoot, C> {

    /**
     * Handles the given command and returns the aggregate root containing uncommitted events.
     * The CommandBus is responsible for persisting the events.
     * 
     * @param aggregate The aggregate to perform the command on (usually rebuilt from history).
     * @param command The command payload.
     * @return Uni of the modified aggregate.
     */
    Uni<A> handle(A aggregate, C command);
}
