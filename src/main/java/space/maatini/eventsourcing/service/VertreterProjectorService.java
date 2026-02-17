package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.VertreterAggregate;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async Projector that reads unprocessed events and updates the
 * VertreterAggregate via a Handler-Registry pattern.
 * Listens for PostgreSQL LISTEN/NOTIFY for near-realtime processing.
 */
@ApplicationScoped
public class VertreterProjectorService {

    private static final int BATCH_SIZE = 50;
    private static final long STARTUP_DELAY_MS = 2000;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long YIELD_DELAY_MS = 10;

    private final PgPool pgPool;
    private final Vertx vertx;
    private final List<VertreterEventHandler> handlers;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rerunRequested = new AtomicBoolean(false);
    private volatile SqlConnection listeningConnection;

    @Inject
    public VertreterProjectorService(PgPool pgPool, Vertx vertx, Instance<VertreterEventHandler> handlerInstances) {
        this.pgPool = pgPool;
        this.vertx = vertx;
        this.handlers = handlerInstances.stream().toList();
    }

    public void onStart(@Observes StartupEvent ev) {
        Log.infof("VertreterProjectorService started with %d handler(s)", handlers.size());
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
        return handlers.stream()
                .filter(h -> h.canHandle(event.getType()))
                .findFirst()
                .map(handler -> handler.handle(event)
                        .chain(() -> markAsProcessed(event)))
                .orElseGet(() -> markAsProcessed(event));
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
        return VertreterAggregate.deleteAll()
                .chain(() -> {
                    if (fromEventId != null) {
                        // Find the event's createdAt, then reset all events from that point
                        return CloudEvent.<CloudEvent>findById(fromEventId)
                                .chain(refEvent -> {
                                    if (refEvent == null) {
                                        // Event not found – reset all
                                        return CloudEvent.update("UPDATE CloudEvent SET processedAt = null");
                                    }
                                    return CloudEvent.update(
                                            "UPDATE CloudEvent SET processedAt = null WHERE createdAt >= ?1",
                                            refEvent.getCreatedAt());
                                });
                    } else {
                        return CloudEvent.update("UPDATE CloudEvent SET processedAt = null");
                    }
                })
                .invoke(count -> Log.infof("Replay initiated: reset %d events for re-processing", count));
    }
}
