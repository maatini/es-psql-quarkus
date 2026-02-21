package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import space.maatini.eventsourcing.entity.JsonAggregate;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenericAggregateService {

    public Uni<JsonObject> findByTypeAndId(String type, String id) {
        return JsonAggregate.findByTypeAndId(type, id)
                .map(agg -> agg != null ? agg.state : null);
    }

    public Uni<List<JsonObject>> listByType(String type) {
        return JsonAggregate.find("type", type).list()
                .map(list -> ((List<JsonAggregate>) (Object) list).stream()
                        .map(agg -> agg.state)
                        .collect(Collectors.toList()));
    }
}
