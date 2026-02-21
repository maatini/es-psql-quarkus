package space.maatini.eventsourcing.service;

import java.util.List;
import java.util.UUID;

import space.maatini.eventsourcing.dto.CloudEventDTO;
import space.maatini.eventsourcing.entity.CloudEvent;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Service for storing and retrieving CloudEvents.
 * The aggregation is handled by the ProjectionService, not this
 * service.
 */
@ApplicationScoped
public class EventService {

    private final Logger log;

    public EventService(Logger log) {
        this.log = log;
    }

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
        return CloudEvent.<CloudEvent>findById(event.id())
                .flatMap(existing -> {
                    if (existing != null) {
                        log.infof("Event %s already exists, returning existing (idempotent)", event.id());
                        return Uni.createFrom().item(new EventResult(existing, true));
                    }

                    // Convert Map to JsonObject for JSONB storage
                    JsonObject dataJson = new JsonObject(event.data());

                    // Create new event entity
                    CloudEvent entity = new CloudEvent();
                    entity.setId(event.id());
                    entity.setSource(event.source());
                    entity.setSpecversion(event.specversion());
                    entity.setType(event.type());
                    entity.setSubject(event.subject());
                    entity.setTime(event.time());
                    entity.setDatacontenttype(event.datacontenttype());
                    entity.setDataschema(event.dataschema());
                    entity.setDataVersion(event.dataVersion());
                    entity.setData(dataJson);

                    // Persist - the projector service will handle aggregation
                    return entity.<CloudEvent>persist()
                            .map(persisted -> {
                                log.infof("Event %s stored successfully, type=%s", event.id(), event.type());
                                return new EventResult(persisted, false);
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
    public record EventResult(CloudEvent event, boolean alreadyExisted) {
    }
}
