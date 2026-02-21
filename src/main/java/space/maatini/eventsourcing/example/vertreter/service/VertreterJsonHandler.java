package space.maatini.eventsourcing.example.vertreter.service;

import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.service.HandlesEvents;
import space.maatini.eventsourcing.service.JsonAggregateHandler;

@ApplicationScoped
@HandlesEvents(value = "space.maatini.vertreter.", aggregateType = "vertreter")
public class VertreterJsonHandler implements JsonAggregateHandler {

    @Override
    public String getAggregateType() {
        return "vertreter";
    }

    @Override
    public JsonObject apply(JsonObject state, CloudEvent event) {
        String type = event.getType();
        JsonObject data = event.getData();

        if (type.endsWith(".deleted")) {
            return null; // Signals deletion of the aggregate state
        }

        JsonObject newState = state.copy();

        if (type.endsWith(".created") || type.endsWith(".updated")) {
            newState.put("id", data.getString("id"));
            if (data.containsKey("name")) {
                newState.put("name", data.getString("name"));
            }
            if (data.containsKey("email")) {
                newState.put("email", data.getString("email"));
            }

            JsonObject vp = data.getJsonObject("vertretenePerson");
            if (vp != null) {
                newState.put("vertretenePerson", vp.copy());
            }
        }

        return newState;
    }
}
