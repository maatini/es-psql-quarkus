package space.maatini.eventsourcing.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.entity.CloudEvent;

import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicBoolean;

@ApplicationScoped
public class EventBatchProcessor {
    private static final int BATCH_SIZE = 50;
    private static final int MAX_RETRIES = 5;
    private static final long YIELD_DELAY_MS = 10;

    private final Vertx vertx;
    private final EventHandlerRegistry handlerRegistry;
    private final ProjectionMetrics projectionMetrics;
    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rerunRequested = new AtomicBoolean(false);

    @Inject
    public EventBatchProcessor(Vertx vertx, EventHandlerRegistry handlerRegistry, ProjectionMetrics projectionMetrics) {
        this.vertx = vertx;
        this.handlerRegistry = handlerRegistry;
        this.projectionMetrics = projectionMetrics;
    }

    public void triggerBackgroundProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            runProcessingChain();
        } else {
            rerunRequested.set(true);
        }
    }

    public Uni<Integer> triggerManualBatch() {
        if (isProcessing.compareAndSet(false, true)) {
            return processBatch()
                    .onTermination().invoke(() -> isProcessing.set(false));
        } else {
            return Uni.createFrom().emitter(emitter -> {
                vertx.setTimer(100, id -> triggerManualBatch().subscribe().with(emitter::complete, emitter::fail));
            });
        }
    }

    private void runProcessingChain() {
        processBatch()
                .subscribe().with(
                        count -> {
                            if (count > 0) Log.debugf("Processed %d events", count);
                            if (count == BATCH_SIZE || rerunRequested.compareAndSet(true, false)) {
                                vertx.setTimer(YIELD_DELAY_MS, id -> runProcessingChain());
                            } else {
                                isProcessing.set(false);
                            }
                        },
                        failure -> {
                            Log.error("Failed to process batch", failure);
                            isProcessing.set(false);
                        });
    }

    @WithTransaction
    protected Uni<Integer> processBatch() {
        return CloudEvent.findUnprocessed(BATCH_SIZE)
                .chain(events -> {
                    if (events.isEmpty()) return Uni.createFrom().item(0);
                    return Multi.createFrom().iterable(events)
                            .onItem().transformToUniAndConcatenate(this::processEvent)
                            .collect().last()
                            .map(v -> events.size());
                });
    }

    private Uni<Void> processEvent(CloudEvent event) {
        String eventType = event.getType();
        return handlerRegistry.getHandlers().entrySet().stream()
                .filter(e -> eventType.startsWith(e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .filter(h -> h.canHandle(eventType))
                .findFirst()
                .map(h -> h.handle(event)
                        .chain(() -> markAsProcessed(event))
                        .onFailure().recoverWithUni(t -> handleFailure(event, t)))
                .orElseGet(() -> markAsProcessed(event));
    }

    private Uni<Void> handleFailure(CloudEvent event, Throwable t) {
        Log.errorf(t, "Failed to process event %s", event.getId());
        projectionMetrics.incrementFailed();
        event.setFailedAt(OffsetDateTime.now());
        event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
        event.setErrorMessage(t.getMessage());
        if (event.getRetryCount() >= MAX_RETRIES) {
            projectionMetrics.incrementDeadLetter();
            Log.warnf("Event %s moved to dead-letter after %d retries", event.getId(), event.getRetryCount());

            return CloudEvent.getSession().chain(session ->
                session.createNativeQuery(
                    "INSERT INTO events_dead_letter (event_id, type, subject, reason, error_message, retry_count) " +
                    "VALUES (:id, :type, :sub, :reason, :err, :retries)"
                )
                .setParameter("id", event.getId())
                .setParameter("type", event.getType())
                .setParameter("sub", event.getSubject())
                .setParameter("reason", "Max retries exceeded")
                .setParameter("err", t.getMessage())
                .setParameter("retries", event.getRetryCount())
                .executeUpdate()
            ).chain(() -> event.delete().replaceWithVoid());
        }
        return event.persist().replaceWithVoid();
    }

    private Uni<Void> markAsProcessed(CloudEvent event) {
        event.setProcessedAt(OffsetDateTime.now());
        projectionMetrics.incrementProcessed();
        return event.persist().replaceWithVoid();
    }
}
