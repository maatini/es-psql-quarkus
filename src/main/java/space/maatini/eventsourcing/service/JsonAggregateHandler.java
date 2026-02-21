package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.JsonAggregate;

import java.time.OffsetDateTime;

public interface JsonAggregateHandler extends EventHandler {

    String getAggregateType();   // z.B. "vertreter"

    @Override
    default boolean canHandle(String eventType) {
        return eventType.startsWith("space.maatini." + getAggregateType() + ".");
    }

    /**
     * Der Handler bekommt das aktuelle State-JSON + das neue Event
     * und gibt das neue State-JSON zurück (immutable Style empfohlen).
     */
    JsonObject apply(JsonObject currentState, CloudEvent event);

    @Override
    default Uni<Void> handle(CloudEvent event) {
        String id = event.getData().getString("id");
        if (id == null) return Uni.createFrom().voidItem();

        String type = getAggregateType();

        return JsonAggregate.findByTypeAndId(type, id)
                .chain(existing -> {
                    JsonObject current = existing != null ? existing.state.copy() : new JsonObject();
                    JsonObject newState = apply(current, event);

                    // Wenn null zurückgegeben wird, könnte das Löschen bedeuten
                    if (newState == null) {
                        return existing != null ? existing.delete().replaceWithVoid() : Uni.createFrom().voidItem();
                    }

                    JsonAggregate agg = existing != null ? existing : new JsonAggregate();
                    agg.type = type;
                    agg.id = id;
                    agg.state = newState;
                    agg.version = (existing != null ? existing.version : 0) + 1;
                    agg.lastEventId = event.getId();
                    agg.updatedAt = event.getTime() != null ? event.getTime() : OffsetDateTime.now();

                    return agg.persist().replaceWithVoid();
                });
    }
}
