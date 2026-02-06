package space.maatini.eventsourcing.service;

import java.util.List;
import java.util.UUID;

import space.maatini.eventsourcing.dto.CloudEventDTO;
import space.maatini.eventsourcing.entity.CloudEvent;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

/**
 * Service for storing and retrieving CloudEvents.
 * The aggregation is handled by PostgreSQL triggers, not this service.
 */
@ApplicationScoped
public class EventService {

    @Inject
    Logger log;

    /**
     * Store a new event. Idempotent - returns existing event if already stored.
     * 
     * @param dto The CloudEvent DTO to store
     * @return The stored or existing event
     */
    @WithTransaction
    public Uni<EventResult> storeEvent(CloudEventDTO dto) {
        // Apply defaults
        CloudEventDTO event = dto.withDefaults();
        
        log.debugf("Storing event: id=%s, type=%s, subject=%s", event.id(), event.type(), event.subject());
        
        // Check for idempotency - if event already exists, return it
        return CloudEvent.findById(event.id())
            .flatMap(existing -> {
                if (existing != null) {
                    log.infof("Event %s already exists, returning existing (idempotent)", event.id());
                    return Uni.createFrom().item(new EventResult((CloudEvent) existing, true));
                }
                
                // Convert Map to JsonObject for JSONB storage
                JsonObject dataJson = new JsonObject(event.data());
                
                // Create new event entity
                CloudEvent entity = new CloudEvent();
                entity.id = event.id();
                entity.source = event.source();
                entity.specversion = event.specversion();
                entity.type = event.type();
                entity.subject = event.subject();
                entity.time = event.time();
                entity.datacontenttype = event.datacontenttype();
                entity.dataschema = event.dataschema();
                entity.data = dataJson;
                
                // Persist - the PostgreSQL trigger will handle aggregation
                return entity.persist()
                    .map(persisted -> {
                        log.infof("Event %s stored successfully, type=%s", event.id(), event.type());
                        return new EventResult((CloudEvent) persisted, false);
                    });
            });
    }

    /**
     * Find an event by ID.
     */
    public Uni<CloudEvent> findById(UUID id) {
        return CloudEvent.findById(id);
    }

    /**
     * Find all events for a given subject (aggregate ID).
     */
    public Uni<List<CloudEvent>> findBySubject(String subject) {
        return CloudEvent.findBySubject(subject);
    }

    /**
     * Find all events of a given type.
     */
    public Uni<List<CloudEvent>> findByType(String type) {
        return CloudEvent.findByType(type);
    }

    /**
     * Result of storing an event.
     */
    public record EventResult(CloudEvent event, boolean alreadyExisted) {}
}
