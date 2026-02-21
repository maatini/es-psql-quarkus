package space.maatini.eventsourcing.service;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.enterprise.context.ApplicationScoped;
import space.maatini.eventsourcing.entity.JsonAggregate;

import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class GenericAggregateService {

    @WithSession
    public Uni<JsonObject> findByTypeAndId(String type, String id) {
        return JsonAggregate.findByTypeAndId(type, id)
                .map(agg -> agg != null ? new JsonObject(agg.state) : null);
    }

    @WithSession
    public Uni<List<JsonObject>> listByType(String type) {
        return JsonAggregate.find("type", type).list()
                .map(list -> ((List<JsonAggregate>) (Object) list).stream()
                        .map(agg -> new JsonObject(agg.state))
                        .collect(Collectors.toList()));
    }
}
