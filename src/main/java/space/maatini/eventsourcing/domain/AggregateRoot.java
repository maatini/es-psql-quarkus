package space.maatini.eventsourcing.domain;

import space.maatini.eventsourcing.entity.CloudEvent;
import java.util.ArrayList;
import java.util.List;

public abstract class AggregateRoot {
    private final String id;
    private int version = 0;
    private final List<CloudEvent> uncommittedEvents = new ArrayList<>();

    protected AggregateRoot(String id) {
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

    protected abstract void mutate(CloudEvent event);
}
