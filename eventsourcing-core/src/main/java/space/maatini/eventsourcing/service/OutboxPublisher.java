package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import space.maatini.eventsourcing.entity.OutboxEvent;

/**
 * Interface for publishing outbox events to external systems 
 * (Kafka, RabbitMQ, SNS, etc.).
 */
public interface OutboxPublisher {
    
    /**
     * Publishes a single outbox event.
     * 
     * @param event The outbox event to publish.
     * @return Uni representing completion of the publishing process.
     */
    Uni<Void> publish(OutboxEvent event);
}
