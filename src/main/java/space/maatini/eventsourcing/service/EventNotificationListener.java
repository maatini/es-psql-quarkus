package space.maatini.eventsourcing.service;

import io.quarkus.logging.Log;
import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.SqlConnection;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

import io.quarkus.arc.profile.UnlessBuildProfile;

@ApplicationScoped
@UnlessBuildProfile("test")
public class EventNotificationListener {
    private static final long STARTUP_DELAY_MS = 2000;
    private static final long RECONNECT_DELAY_MS = 5000;

    private final PgPool pgPool;
    private final Vertx vertx;
    private final EventBatchProcessor batchProcessor;
    private final EventHandlerRegistry handlerRegistry;

    private volatile SqlConnection listeningConnection;

    @Inject
    public EventNotificationListener(PgPool pgPool, Vertx vertx, EventBatchProcessor batchProcessor, EventHandlerRegistry handlerRegistry) {
        this.pgPool = pgPool;
        this.vertx = vertx;
        this.batchProcessor = batchProcessor;
        this.handlerRegistry = handlerRegistry;
    }

    public void onStart(@Observes StartupEvent ev) {
        Log.infof("EventNotificationListener started with %d aggregate domain(s)", handlerRegistry.size());
        vertx.setTimer(STARTUP_DELAY_MS, id -> listenForNotifications());
    }

    public void onStop(@Observes ShutdownEvent ev) {
        if (listeningConnection != null) {
            listeningConnection.close()
                    .subscribe().with(v -> Log.info("Closed listener"), f -> Log.warn("Failed to close listener", f));
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
                            io.vertx.core.impl.ContextInternal context = ((io.vertx.core.impl.ContextInternal) vertx
                                    .getDelegate().getOrCreateContext()).duplicate();
                            VertxContextSafetyToggle.setContextSafe(context, true);
                            context.runOnContext(v -> batchProcessor.triggerBackgroundProcessing());
                        });
                    }
                    conn.query("LISTEN events_channel").execute()
                            .subscribe().with(item -> Log.info("Listening on 'events_channel'"), failure -> Log.error("Failed to LISTEN", failure));
                })
                .subscribe().with(
                        conn -> conn.closeHandler(() -> {
                            Log.warn("Postgres connection closed. Reconnecting in 5s...");
                            vertx.setTimer(RECONNECT_DELAY_MS, id -> listenForNotifications());
                        }),
                        failure -> {
                            Log.error("Failed to connect to Postgres. Retrying in 5s...", failure);
                            vertx.setTimer(RECONNECT_DELAY_MS, id -> listenForNotifications());
                        });
    }
}
