package space.maatini.eventsourcing.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import space.maatini.eventsourcing.entity.OutboxEvent;

import java.time.OffsetDateTime;

/**
 * Periodically checks the outbox_events table for PENDING events
 * and dispatches them (e.g. via an external message broker).
 */
@ApplicationScoped
public class OutboxScheduler {

    private final Logger log = Logger.getLogger(OutboxScheduler.class);

    // Lock to prevent concurrent execution if schedule overlaps
    private boolean isRunning = false;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    public void processOutboxEvents() {
        if (isRunning) return;
        isRunning = true;
        
        processPending()
            .subscribe().with(
                success -> isRunning = false,
                failure -> {
                    log.error("Error processing outbox events", failure);
                    isRunning = false;
                }
            );
    }

    @WithTransaction
    public Uni<Void> processPending() {
        // Fetch PENDING events limited to 50
        return OutboxEvent.<OutboxEvent>find("status = 'PENDING' ORDER BY createdAt ASC")
            .page(0, 50)
            .list()
            .chain(events -> {
                if (events.isEmpty()) {
                    return Uni.createFrom().voidItem();
                }

                log.infof("Processing %d pending outbox events", events.size());

                return Multi.createFrom().iterable(events)
                    .onItem().transformToUniAndConcatenate(event -> {
                        // TODO: Actually send the event to Kafka, RabbitMQ, SNS etc.
                        log.debugf("Dispatching outbound event %s / topic: %s", event.id, event.topic);
                        
                        // Mark as SENT
                        event.status = "SENT";
                        event.processedAt = OffsetDateTime.now();
                        return event.persist();
                    })
                    .collect().last().replaceWithVoid();
            });
    }
}
