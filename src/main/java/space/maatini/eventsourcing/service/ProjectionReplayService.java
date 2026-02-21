package space.maatini.eventsourcing.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.entity.CloudEvent;
import jakarta.persistence.Entity;

import java.util.UUID;

@ApplicationScoped
public class ProjectionReplayService {

    private final EventHandlerRegistry handlerRegistry;

    @Inject
    public ProjectionReplayService(EventHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @WithTransaction
    public Uni<Integer> replayAll(UUID fromEventId) {
        Log.info("Starting generic replay for all aggregates");

        // KRITISCHER FIX – Replay-Bug behoben
        // Verwende den tatsächlichen Entity-Namen (aus @Entity-Annotation) statt clazz.getSimpleName()
        Uni<Void> deleteAll = Multi.createFrom().iterable(handlerRegistry.getAggregateClasses())
                .onItem().transformToUniAndConcatenate(clazz -> {
                    String entityName = clazz.getSimpleName();
                    Entity entityAnnotation = clazz.getAnnotation(Entity.class);
                    if (entityAnnotation != null && !entityAnnotation.name().isEmpty()) {
                        entityName = entityAnnotation.name();
                    }
                    final String finalEntityName = entityName;
                    return CloudEvent.getSession()
                            .chain(s -> s.createQuery("DELETE FROM " + finalEntityName).executeUpdate())
                            .replaceWithVoid();
                })
                .collect().last().replaceWithVoid();

        return deleteAll.chain(() -> {
            String update = "UPDATE CloudEvent SET processedAt = null, failedAt = null, retryCount = 0, errorMessage = null";
            if (fromEventId != null) {
                return CloudEvent.<CloudEvent>findById(fromEventId)
                        .chain(ref -> ref != null
                                ? CloudEvent.update(update + " WHERE createdAt >= ?1", ref.getCreatedAt())
                                : CloudEvent.update(update));
            }
            return CloudEvent.update(update);
        }).invoke(count -> Log.infof("Replay finished – %d events reset", count));
    }
}
