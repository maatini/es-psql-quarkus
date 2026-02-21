package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import space.maatini.eventsourcing.entity.OutboxEvent;

/**
 * Default implementation of OutboxPublisher that logs the event.
 * In a real-world scenario, you would replace this with a Kafka, 
 * RabbitMQ, or NATS implementation.
 */
@ApplicationScoped
public class LogOutboxPublisher implements OutboxPublisher {

    private static final Logger LOG = Logger.getLogger(LogOutboxPublisher.class);

    @Override
    public Uni<Void> publish(OutboxEvent event) {
        // Here you would implement integration with a message broker
        LOG.infof(">>> PUBLISHING EVENT TO EXTERNAL SYSTEM: Topic: %s, Payload: %s", 
                event.topic, event.payload);
        
        return Uni.createFrom().voidItem();
    }
}
