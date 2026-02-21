package space.maatini.eventsourcing.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.entity.CloudEvent;
import io.smallrye.mutiny.Uni;

@ApplicationScoped
public class ProjectionMetrics {

    private final Counter processedCounter;
    private final Counter failedCounter;
    private final Counter deadLetterCounter;
    private volatile double currentLagSeconds = 0.0;

    @Inject
    public ProjectionMetrics(MeterRegistry meterRegistry) {
        processedCounter = meterRegistry.counter("projection.processed.events");
        failedCounter = meterRegistry.counter("projection.failed.events");
        deadLetterCounter = meterRegistry.counter("projection.deadletter.events");

        Gauge.builder("projection.lag.seconds", this, ProjectionMetrics::getLagSeconds)
                .description("Current projection lag in seconds")
                .register(meterRegistry);
    }

    public void incrementProcessed() {
        processedCounter.increment();
    }

    public void incrementFailed() {
        failedCounter.increment();
    }

    public void incrementDeadLetter() {
        deadLetterCounter.increment();
    }

    public double getLagSeconds() {
        return currentLagSeconds;
    }

    @Scheduled(every = "10s")
    @WithSession
    Uni<Void> updateLagMonitor() {
        return CloudEvent.getSession().chain(s ->
            s.createNativeQuery("SELECT EXTRACT(EPOCH FROM (NOW() - MAX(created_at))) FROM events WHERE processed_at IS NULL")
             .getSingleResult()
        ).invoke(
            res -> this.currentLagSeconds = (res != null) ? ((Number) res).doubleValue() : 0.0
        ).onFailure().invoke(
            err -> Log.warn("Failed to calculate projection lag", err)
        ).replaceWithVoid();
    }
}
