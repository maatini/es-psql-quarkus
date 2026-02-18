package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.VertreterAggregate;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.OffsetDateTime;

@ApplicationScoped
@HandlesEvents(value = "space.maatini.vertreter.", aggregate = VertreterAggregate.class)
public class VertreterCreatedOrUpdatedHandler implements AggregateEventHandler<VertreterAggregate> {

    private static final String PREFIX = "space.maatini.vertreter.";
    private static final String CREATED = PREFIX + "created";
    private static final String UPDATED = PREFIX + "updated";

    @Override
    public boolean canHandle(String eventType) {
        return CREATED.equals(eventType) || UPDATED.equals(eventType);
    }

    @Override
    public Uni<Void> handle(CloudEvent event) {
        JsonObject data = event.getData();
        String id = data.getString("id");

        if (id == null) {
            return Uni.createFrom().voidItem();
        }

        return VertreterAggregate.<VertreterAggregate>findById(id)
                .chain(existing -> {
                    VertreterAggregate agg = existing != null ? existing : new VertreterAggregate();
                    agg.setId(id);

                    // Patch semantics: only update fields present in event data
                    if (data.containsKey("name"))
                        agg.setName(data.getString("name"));
                    if (data.containsKey("email"))
                        agg.setEmail(data.getString("email"));

                    JsonObject vp = data.getJsonObject("vertretenePerson");
                    if (vp != null) {
                        if (vp.containsKey("id"))
                            agg.setVertretenePersonId(vp.getString("id"));
                        if (vp.containsKey("name"))
                            agg.setVertretenePersonName(vp.getString("name"));
                    }

                    agg.setUpdatedAt(event.getTime() != null ? event.getTime() : OffsetDateTime.now());
                    agg.setEventId(event.getId());
                    agg.setVersion((agg.getVersion() == null ? 0 : agg.getVersion()) + 1);

                    return agg.persist();
                })
                .replaceWithVoid();
    }
}