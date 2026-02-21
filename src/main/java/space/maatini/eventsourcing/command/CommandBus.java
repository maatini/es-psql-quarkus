package space.maatini.eventsourcing.command;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.domain.DomainAggregateRoot;
import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.service.AggregateSnapshotService;
import io.vertx.core.json.JsonObject;

import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;

/**
 * Dynamically routes commands to their appropriate handlers and handles persistence.
 */
@ApplicationScoped
public class CommandBus {

    private final Map<Class<?>, CommandHandler<?, ?>> handlers = new HashMap<>();
    private final AggregateSnapshotService snapshotService;

    @Inject
    public CommandBus(@Any Instance<CommandHandler<?, ?>> handlerInstances, AggregateSnapshotService snapshotService) {
        this.snapshotService = snapshotService;
        for (CommandHandler<?, ?> handler : handlerInstances) {
            Class<?> beanClass = handler.getClass();
            // In Quarkus, the bean class might be a proxy. We need to find the actual annotation.
            HandlesCommand annotation = null;
            
            // Try traversing class hierarchy if it's a proxy
            Class<?> currentClass = beanClass;
            while(currentClass != null && currentClass != Object.class) {
                 annotation = currentClass.getAnnotation(HandlesCommand.class);
                 if (annotation != null) break;
                 currentClass = currentClass.getSuperclass();
            }

            if (annotation != null) {
                handlers.put(annotation.value(), handler);
            } else {
                throw new IllegalStateException("CommandHandler " + beanClass.getName() + " is missing @HandlesCommand annotation");
            }
        }
    }

    /**
     * Dispatches a command to the corresponding handler.
     * 
     * @param aggregateId The ID of the aggregate this command orchestrates.
     * @param aggregateClass The domain class of the aggregate to rebuild.
     * @param command The command payload.
     * @param <A> The aggregate type.
     * @param <C> The command type.
     * @return Uni representing completion.
     */
    @WithTransaction
    @SuppressWarnings("unchecked")
    public <A extends DomainAggregateRoot, C> Uni<Void> dispatch(String aggregateId, Class<A> aggregateClass, C command) {
        CommandHandler<A, C> handler = (CommandHandler<A, C>) handlers.get(command.getClass());

        if (handler == null) {
            return Uni.createFrom().failure(new IllegalArgumentException("No handler registered for command: " + command.getClass().getName()));
        }

        return loadAggregate(aggregateId, aggregateClass)
                .chain(aggregate -> handler.handle(aggregate, command))
                .chain(aggregate -> {
                    Uni<Void> saved = saveEvents(aggregate);
                    // Create snapshot every 100 versions
                    if (aggregate.getVersion() > 0 && aggregate.getVersion() % 100 == 0) {
                        CloudEvent lastEvent = aggregate.getUncommittedEvents().getLast();
                        return saved.chain(() -> snapshotService.saveSnapshot(aggregate, lastEvent.getId()));
                    }
                    return saved;
                });
    }

    private <A extends DomainAggregateRoot> Uni<A> loadAggregate(String aggregateId, Class<A> aggregateClass) {
        return snapshotService.getLatestSnapshot(aggregateId, aggregateClass.getSimpleName())
            .chain(snapshot -> {
                int startOffset = snapshot != null ? snapshot.aggregateVersion : 0;
                
                return CloudEvent.<CloudEvent>find("subject = ?1 ORDER BY createdAt ASC", aggregateId)
                        .range(startOffset, Integer.MAX_VALUE - 1)
                        .list()
                        .map(events -> {
                            try {
                                Constructor<A> constructor = aggregateClass.getConstructor(String.class);
                                A aggregate = constructor.newInstance(aggregateId);
                                
                                if (snapshot != null) {
                                    aggregate.restoreSnapshot(new JsonObject(snapshot.state));
                                    aggregate.setVersion(snapshot.aggregateVersion);
                                }
                                
                                events.forEach(aggregate::apply);
                                return aggregate;
                            } catch (Exception e) {
                                throw new RuntimeException("Failed to instantiate aggregate " + aggregateClass.getName(), e);
                            }
                        });
            });
    }

    private Uni<Void> saveEvents(DomainAggregateRoot aggregate) {
        if (aggregate.getUncommittedEvents().isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return Multi.createFrom().iterable(aggregate.getUncommittedEvents())
                .onItem().transformToUniAndConcatenate(event -> event.persist())
                .collect().last().replaceWithVoid();
    }
}
