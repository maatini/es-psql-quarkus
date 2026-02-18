package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async Projector that reads unprocessed events and updates the
 * aggregate state via a Handler-Registry pattern.
 * Listens for PostgreSQL LISTEN/NOTIFY for near-realtime processing.
 */
@ApplicationScoped
public class ProjectionService {

    private static final int BATCH_SIZE = 50;
    private static final long STARTUP_DELAY_MS = 2000;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long YIELD_DELAY_MS = 10;

    private final PgPool pgPool;
    private final Vertx vertx;
    private final java.util.Map<String, java.util.List<EventHandler>> handlerRegistry = new java.util.HashMap<>();
    private final java.util.Set<Class<? extends space.maatini.eventsourcing.entity.AggregateRoot>> aggregateClasses = new java.util.HashSet<>();

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rerunRequested = new AtomicBoolean(false);
    private volatile SqlConnection listeningConnection;

    @Inject
    public ProjectionService(PgPool pgPool, Vertx vertx, Instance<AggregateEventHandler<?>> handlerInstances) {
        this.pgPool = pgPool;
        this.vertx = vertx;
        handlerInstances.handles().forEach(handle -> {
            AggregateEventHandler<?> handler = handle.get();
            Class<?> beanClass = handle.getBean().getBeanClass();
            HandlesEvents annotation = beanClass.getAnnotation(HandlesEvents.class);
            if (annotation != null) {
                String prefix = annotation.value();
                handlerRegistry.computeIfAbsent(prefix, k -> new java.util.ArrayList<>()).add(handler);
                aggregateClasses.add(annotation.aggregate());
            }
        });
    }

    public void onStart(@Observes StartupEvent ev) {
        Log.infof("ProjectionService started with %d aggregate domain(s)", handlerRegistry.size());
        vertx.setTimer(STARTUP_DELAY_MS, id -> listenForNotifications());
    }

    public void onStop(@Observes ShutdownEvent ev) {
        if (listeningConnection != null) {
            listeningConnection.close()
                    .subscribe().with(
                            v -> Log.info("Closed Postgres listener connection"),
                            f -> Log.warn("Failed to close listener connection", f));
        }
    }

    private void listenForNotifications() {
        Log.info("Starting listener for 'events_channel'...");

        pgPool.getConnection()
                .invoke(conn -> {
                    this.listeningConnection = conn;

                    io.vertx.sqlclient.SqlConnection delegate = conn.getDelegate();
                    if (delegate instanceof io.vertx.pgclient.PgConnection pgConn) {
                        pgConn.notificationHandler(notification -> {
                            Log.debugf("Received notification: %s", notification.getPayload());
                            // Hibernate Reactive requires a duplicated context flagged as "safe"
                            io.vertx.core.impl.ContextInternal context = ((io.vertx.core.impl.ContextInternal) vertx
                                    .getDelegate().getOrCreateContext()).duplicate();
                            VertxContextSafetyToggle.setContextSafe(context, true);
                            context.runOnContext(v -> triggerBackgroundProcessing());
                        });
                    } else {
                        Log.warn("Connection is not a Postgres connection, cannot listen for notifications");
                    }

                    conn.query("LISTEN events_channel").execute()
                            .subscribe().with(
                                    item -> Log.info("Listening on 'events_channel'"),
                                    failure -> Log.error("Failed to LISTEN", failure));
                })
                .subscribe().with(
                        conn -> {
                            conn.closeHandler(() -> {
                                Log.warn("Postgres connection closed. Reconnecting in 5s...");
                                vertx.setTimer(RECONNECT_DELAY_MS, id -> listenForNotifications());
                            });
                        },
                        failure -> {
                            Log.error("Failed to connect to Postgres. Retrying in 5s...", failure);
                            vertx.setTimer(RECONNECT_DELAY_MS, id -> listenForNotifications());
                        });
    }

    private void triggerBackgroundProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            runProcessingChain();
        } else {
            rerunRequested.set(true);
        }
    }

    /**
     * Manual trigger for a single batch, respects the processing lock.
     * If already processing, it waits and retries to ensure a deterministic result
     * for the caller (tests).
     */
    public Uni<Integer> triggerManualBatch() {
        if (isProcessing.compareAndSet(false, true)) {
            return processBatch()
                    .onTermination().invoke(() -> isProcessing.set(false));
        } else {
            // Busy, poll-and-retry on Vert.x EventLoop
            return Uni.createFrom().emitter(emitter -> {
                vertx.setTimer(100, id -> {
                    triggerManualBatch().subscribe().with(emitter::complete, emitter::fail);
                });
            });
        }
    }

    private void runProcessingChain() {
        processBatch()
                .subscribe().with(
                        count -> {
                            if (count > 0)
                                Log.debugf("Processed %d events (reactive)", count);

                            if (count == BATCH_SIZE || rerunRequested.compareAndSet(true, false)) {
                                vertx.setTimer(YIELD_DELAY_MS, id -> runProcessingChain());
                            } else {
                                isProcessing.set(false);
                            }
                        },
                        failure -> {
                            Log.error("Failed to process event from notification", failure);
                            isProcessing.set(false);
                        });
    }

    @WithTransaction
    protected Uni<Integer> processBatch() {
        return CloudEvent.findUnprocessed(BATCH_SIZE)
                .chain(events -> {
                    if (events.isEmpty()) {
                        return Uni.createFrom().item(0);
                    }
                    return Multi.createFrom().iterable(events)
                            .onItem().transformToUniAndConcatenate(this::processEvent)
                            .collect().last()
                            .map(v -> events.size());
                });
    }

    private Uni<Void> processEvent(CloudEvent event) {
        String eventType = event.getType();
        return handlerRegistry.entrySet().stream()
                .filter(entry -> eventType.startsWith(entry.getKey()))
                .flatMap(entry -> entry.getValue().stream())
                .filter(h -> h.canHandle(eventType))
                .findFirst()
                .map(handler -> handler.handle(event)
                        .chain(() -> markAsProcessed(event))
                        .onFailure().recoverWithUni(t -> handleFailure(event, t)))
                .orElseGet(() -> markAsProcessed(event));
    }

    private Uni<Void> handleFailure(CloudEvent event, Throwable t) {
        Log.errorf(t, "Failed to process event %s (%s)", event.getId(), event.getType());
        event.setFailedAt(OffsetDateTime.now());
        event.setRetryCount((event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1);
        event.setErrorMessage(t.getMessage());
        return event.persist().replaceWithVoid();
    }

    private Uni<Void> markAsProcessed(CloudEvent event) {
        event.setProcessedAt(OffsetDateTime.now());
        return event.persist().replaceWithVoid();
    }

    /**
     * Replays all events by deleting all aggregates, resetting processed_at,
     * and re-processing every event from scratch (or from a specific event ID).
     */
    @WithTransaction
    public Uni<Integer> replayAll(UUID fromEventId) {
        Log.info("Initiating generic replay for all registered aggregates");

        // Delete all data from all known aggregates
        Uni<Void> deleteAggregates = Multi.createFrom().iterable(aggregateClasses)
                .onItem().<Void>transformToUniAndConcatenate(clazz -> {
                    // Using Session to delete all records for the given entity class
                    return space.maatini.eventsourcing.entity.CloudEvent.getSession()
                            .chain(session -> session.createQuery("DELETE FROM " + clazz.getSimpleName())
                                    .executeUpdate())
                            .replaceWithVoid();
                })
                .collect().last().onFailure().recoverWithItem((Void) null);

        return deleteAggregates
                .chain(() -> {
                    if (fromEventId != null) {
                        return CloudEvent.<CloudEvent>findById(fromEventId)
                                .chain(refEvent -> {
                                    if (refEvent == null) {
                                        return CloudEvent.update(
                                                "UPDATE CloudEvent SET processedAt = null, failedAt = null, retryCount = 0, errorMessage = null");
                                    }
                                    return CloudEvent.update(
                                            "UPDATE CloudEvent SET processedAt = null, failedAt = null, retryCount = 0, errorMessage = null WHERE createdAt >= ?1",
                                            refEvent.getCreatedAt());
                                });
                    } else {
                        return CloudEvent.update(
                                "UPDATE CloudEvent SET processedAt = null, failedAt = null, retryCount = 0, errorMessage = null");
                    }
                })
                .invoke(count -> Log.infof("Replay initiated: reset %d events for re-processing", count));
    }
}
