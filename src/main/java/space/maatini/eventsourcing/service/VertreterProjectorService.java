package space.maatini.eventsourcing.service;

import java.time.OffsetDateTime;

import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.VertreterAggregate;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.runtime.ShutdownEvent;

import io.vertx.mutiny.sqlclient.SqlConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.core.Vertx;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async Projector that reads unprocessed events and updates the
 * VertreterAggregate.
 * Replaces the SQL-Trigger logic.
 */
@ApplicationScoped
public class VertreterProjectorService {

    private static final String EVENT_TYPE_PREFIX = "space.maatini.vertreter.";
    private static final String EVENT_TYPE_CREATED = EVENT_TYPE_PREFIX + "created";
    private static final String EVENT_TYPE_UPDATED = EVENT_TYPE_PREFIX + "updated";
    private static final String EVENT_TYPE_DELETED = EVENT_TYPE_PREFIX + "deleted";

    private static final int BATCH_SIZE = 50;
    private static final long STARTUP_DELAY_MS = 2000;
    private static final long RECONNECT_DELAY_MS = 5000;
    private static final long YIELD_DELAY_MS = 10;

    private final PgPool pgPool;
    private final Vertx vertx;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rerunRequested = new AtomicBoolean(false);
    private volatile SqlConnection listeningConnection;

    public VertreterProjectorService(PgPool pgPool, Vertx vertx) {
        this.pgPool = pgPool;
        this.vertx = vertx;
    }

    public void onStart(@Observes StartupEvent ev) {
        // Delay listener start to let Hibernate/Panache warm up pool
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

                    // Access the underlying Vert.x connection to set the notification handler
                    io.vertx.sqlclient.SqlConnection delegate = conn.getDelegate();
                    if (delegate instanceof io.vertx.pgclient.PgConnection pgConn) {
                        pgConn.notificationHandler(notification -> {
                            Log.debugf("Received notification: %s", notification.getPayload());
                            // Run on a 'safe' context by using executeBlocking.
                            // Quarkus flags worker threads as safe for Hibernate Reactive.
                            vertx.executeBlocking(Uni.createFrom().item(() -> {
                                triggerProcessing();
                                return null;
                            })).subscribe().with(v -> {
                            });
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

    private void triggerProcessing() {
        if (isProcessing.compareAndSet(false, true)) {
            runProcessingChain();
        } else {
            rerunRequested.set(true);
        }
    }

    private void runProcessingChain() {
        processBatch()
                .subscribe().with(
                        count -> {
                            if (count > 0)
                                Log.debugf("Processed %d events (reactive)", count);

                            // If we processed a full batch or a rerun was requested, schedule next run.
                            // We use a small delay to yield the event loop and allow other requests to be
                            // processed.
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

    /**
     * Runs every 1 second to process new events.
     * In a real system, you might use a shorter interval or reactive messaging.
     */
    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public Uni<Void> processEvents() {
        return processBatch()
                .onItem().invoke(count -> {
                    if (count > 0)
                        Log.debugf("Processed %d events", count);
                })
                .onFailure().invoke(failure -> Log.error("Failed to process events", failure))
                .replaceWithVoid();
    }

    @WithTransaction
    public Uni<Integer> processBatch() {
        return CloudEvent.findUnprocessed(BATCH_SIZE)
                .chain(events -> {
                    if (events.isEmpty()) {
                        return Uni.createFrom().item(0);
                    }

                    // Process sequentially using Multi to maintain order
                    return Multi.createFrom().iterable(events)
                            .onItem().transformToUniAndConcatenate(this::processEvent)
                            .collect().last()
                            .map(unused -> events.size());
                });
    }

    private Uni<Void> processEvent(CloudEvent event) {
        if (!event.getType().startsWith(EVENT_TYPE_PREFIX)) {
            return markAsProcessed(event);
        }

        JsonObject data = event.getData();
        String id = data.getString("id");

        if (id == null) {
            Log.warnf("Event %s has no ID in data payload, skipping", event.getId());
            return markAsProcessed(event);
        }

        Uni<Void> processingLogic = switch (event.getType()) {
            case EVENT_TYPE_CREATED, EVENT_TYPE_UPDATED -> applyUpsert(event, id, data);
            case EVENT_TYPE_DELETED -> applyDelete(id);
            default -> {
                Log.warnf("Unknown Vertreter event type: %s", event.getType());
                yield Uni.createFrom().voidItem();
            }
        };

        return processingLogic.chain(() -> markAsProcessed(event));
    }

    private Uni<Void> applyUpsert(CloudEvent event, String id, JsonObject data) {
        return VertreterAggregate.<VertreterAggregate>findById(id)
                .chain(existing -> {
                    VertreterAggregate aggregate = existing != null ? existing : new VertreterAggregate();
                    aggregate.setId(id);

                    // Update fields if present in event data (Patch semantics)
                    if (data.containsKey("name"))
                        aggregate.setName(data.getString("name"));
                    if (data.containsKey("email"))
                        aggregate.setEmail(data.getString("email"));

                    // Handle nested vertretenePerson
                    JsonObject vp = data.getJsonObject("vertretenePerson");
                    if (vp != null) {
                        if (vp.containsKey("id"))
                            aggregate.setVertretenePersonId(vp.getString("id"));
                        if (vp.containsKey("name"))
                            aggregate.setVertretenePersonName(vp.getString("name"));
                    }

                    aggregate.setUpdatedAt(event.getTime() != null ? event.getTime() : OffsetDateTime.now());
                    aggregate.setEventId(event.getId());
                    aggregate.setVersion((aggregate.getVersion() == null ? 0 : aggregate.getVersion()) + 1);

                    return aggregate.persist();
                })
                .replaceWithVoid();
    }

    private Uni<Void> applyDelete(String id) {
        return VertreterAggregate.deleteById(id).replaceWithVoid();
    }

    private Uni<Void> markAsProcessed(CloudEvent event) {
        event.setProcessedAt(OffsetDateTime.now());
        return event.persist().replaceWithVoid();
    }
}
