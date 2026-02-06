package de.antigravity.eventsourcing.service;

import java.time.OffsetDateTime;

import de.antigravity.eventsourcing.entity.CloudEvent;
import de.antigravity.eventsourcing.entity.VertreterAggregate;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.hibernate.reactive.mutiny.Mutiny;
import io.vertx.mutiny.pgclient.PgConnection;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.core.Vertx;
import jakarta.enterprise.event.Observes;
import io.quarkus.runtime.StartupEvent;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Async Projector that reads unprocessed events and updates the
 * VertreterAggregate.
 * Replaces the SQL-Trigger logic.
 */
@ApplicationScoped
public class VertreterProjectorService {

    @Inject
    Mutiny.SessionFactory sessionFactory;

    @Inject
    PgPool pgPool;

    @Inject
    Vertx vertx;

    private final AtomicBoolean isProcessing = new AtomicBoolean(false);
    private final AtomicBoolean rerunRequested = new AtomicBoolean(false);
    private volatile PgConnection listeningConnection;

    public void onStart(@Observes StartupEvent ev) {
        // Delay listener start to let Hibernate/Panache warm up pool
        vertx.setTimer(2000, id -> listenForNotifications());
    }

    public void onStop(@Observes io.quarkus.runtime.ShutdownEvent ev) {
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
                .map(PgConnection.class::cast)
                .invoke(conn -> {
                    this.listeningConnection = conn;
                    conn.query("LISTEN events_channel").execute()
                            .subscribe().with(
                                    item -> Log.info("Listening on 'events_channel'"),
                                    failure -> Log.error("Failed to LISTEN", failure));

                    conn.notificationHandler(notification -> {
                        Log.debugf("Received notification: %s", notification.getPayload());
                        triggerProcessing();
                    });
                })
                .subscribe().with(
                        conn -> {
                            conn.closeHandler(() -> {
                                Log.warn("Postgres connection closed. Reconnecting in 5s...");
                                vertx.setTimer(5000, id -> listenForNotifications());
                            });
                        },
                        failure -> {
                            Log.error("Failed to connect to Postgres. Retrying in 5s...", failure);
                            vertx.setTimer(5000, id -> listenForNotifications());
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

                            // If we processed a full batch (50) or a rerun was requested, schedule next run
                            // We use a small delay to yield the event loop and allow other requests to be
                            // processed
                            if (count == 50 || rerunRequested.compareAndSet(true, false)) {
                                vertx.setTimer(10, id -> runProcessingChain());
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
        // Process up to 50 events at a time
        return CloudEvent.findUnprocessed(50)
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
        if (!event.type.startsWith("de.vertreter.")) {
            return markAsProcessed(event);
        }

        JsonObject data = event.data;
        String id = data.getString("id");

        if (id == null) {
            Log.warnf("Event %s has no ID in data payload, skipping", event.id);
            return markAsProcessed(event);
        }

        Uni<Void> processingLogic;
        switch (event.type) {
            case "de.vertreter.created":
            case "de.vertreter.updated":
                processingLogic = applyUpsert(event, id, data);
                break;
            case "de.vertreter.deleted":
                processingLogic = applyDelete(id);
                break;
            default:
                Log.warnf("Unknown Vertreter event type: %s", event.type);
                processingLogic = Uni.createFrom().voidItem();
        }

        return processingLogic.chain(() -> markAsProcessed(event));
    }

    private Uni<Void> applyUpsert(CloudEvent event, String id, JsonObject data) {
        return VertreterAggregate.<VertreterAggregate>findById(id)
                .chain(existing -> {
                    VertreterAggregate aggregate = existing != null ? existing : new VertreterAggregate();
                    aggregate.id = id;

                    // Update fields if present in event data (Patch semantics)
                    if (data.containsKey("name"))
                        aggregate.name = data.getString("name");
                    if (data.containsKey("email"))
                        aggregate.email = data.getString("email");

                    // Handle nested vertretenePerson
                    JsonObject vp = data.getJsonObject("vertretenePerson");
                    if (vp != null) {
                        if (vp.containsKey("id"))
                            aggregate.vertretenePersonId = vp.getString("id");
                        if (vp.containsKey("name"))
                            aggregate.vertretenePersonName = vp.getString("name");
                    }

                    aggregate.updatedAt = event.time != null ? event.time : OffsetDateTime.now();
                    aggregate.eventId = event.id;
                    aggregate.version = (aggregate.version == null ? 0 : aggregate.version) + 1;

                    return aggregate.persist();
                })
                .replaceWithVoid();
    }

    private Uni<Void> applyDelete(String id) {
        return VertreterAggregate.deleteById(id).replaceWithVoid();
    }

    private Uni<Void> markAsProcessed(CloudEvent event) {
        event.processedAt = OffsetDateTime.now();
        return event.persist().replaceWithVoid(); // or update
    }
}
