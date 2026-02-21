package space.maatini.eventsourcing.domain;

import space.maatini.eventsourcing.entity.CloudEvent;
import io.vertx.core.json.JsonObject;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class DomainAggregateRoot {
    private final String id;
    private int version = 0;
    private final List<CloudEvent> uncommittedEvents = new ArrayList<>();

    protected DomainAggregateRoot(String id) {
        this.id = id;
    }

    public String getId() { return id; }
    public int getVersion() { return version; }
    public List<CloudEvent> getUncommittedEvents() { return uncommittedEvents; }

    public void apply(CloudEvent event) {
        mutate(event);
        version++;
    }

    protected void applyNewEvent(CloudEvent event) {
        apply(event);
        uncommittedEvents.add(event);
    }

    /**
     * Emits a new event with the aggregate ID as the subject.
     * 
     * @param type The distinct event type, e.g. "space.maatini.vertreter.created"
     * @param data The payload for the event
     */
    protected void emitEvent(String type, JsonObject data) {
        emitEvent(type, data, this.id);
    }

    /**
     * Emits a new event with a customized subject.
     */
    protected void emitEvent(String type, JsonObject data, String subject) {
        CloudEvent event = new CloudEvent();
        event.setId(UUID.randomUUID());
        event.setType(type);
        event.setSource("/domain/" + this.getClass().getSimpleName().toLowerCase());
        event.setSubject(subject);
        event.setTime(OffsetDateTime.now());
        event.setData(data);
        
        applyNewEvent(event);
    }

    protected abstract void mutate(CloudEvent event);
}
