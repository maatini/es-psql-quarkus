package space.maatini.eventsourcing.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "aggregate_states")
@IdClass(JsonAggregate.JsonAggregateId.class)
public class JsonAggregate extends PanacheEntityBase implements AggregateRoot {

    @Id
    public String type;
    @Id
    public String id;

    @JdbcTypeCode(SqlTypes.JSON)
    public JsonObject state = new JsonObject();

    public Integer version = 0;
    public UUID lastEventId;
    public OffsetDateTime updatedAt = OffsetDateTime.now();

    /**
     * Composite ID class for Hibernate.
     */
    public static class JsonAggregateId implements Serializable {
        public String type;
        public String id;

        public JsonAggregateId() {}
        public JsonAggregateId(String type, String id) {
            this.type = type;
            this.id = id;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            JsonAggregateId that = (JsonAggregateId) o;
            return Objects.equals(type, that.type) && Objects.equals(id, that.id);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, id);
        }
    }

    // Statische Queries
    public static Uni<JsonAggregate> findByTypeAndId(String type, String id) {
        return find("type = ?1 and id = ?2", type, id).firstResult();
    }

    public static Uni<JsonObject> findState(String type, String id) {
        return findByTypeAndId(type, id)
                .map(agg -> agg != null ? agg.state : null);
    }
}
