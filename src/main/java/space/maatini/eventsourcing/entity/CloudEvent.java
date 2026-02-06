package space.maatini.eventsourcing.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import io.vertx.core.json.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * CloudEvents-compliant event entity.
 * @see <a href="https://github.com/cloudevents/spec">CloudEvents Specification</a>
 */
@Entity
@Table(name = "events")
public class CloudEvent extends PanacheEntityBase {

    @Id
    public UUID id;

    @Column(nullable = false)
    public String source;

    @Column(nullable = false)
    public String specversion = "1.0";

    @Column(nullable = false)
    public String type;

    public String subject;

    @Column(nullable = false)
    public OffsetDateTime time = OffsetDateTime.now();

    public String datacontenttype = "application/json";

    public String dataschema;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonObject data;

    @Column(name = "created_at", nullable = false)
    public OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "processed_at")
    public OffsetDateTime processedAt;

    /**
     * Check if an event with this ID already exists (idempotency check).
     */
    public static Uni<Boolean> existsById(UUID id) {
        return find("id", id).count().map(count -> count > 0);
    }

    /**
     * Find events by type.
     */
    public static Uni<java.util.List<CloudEvent>> findByType(String type) {
        return list("type", type);
    }

    /**
     * Find events by subject (aggregate ID).
     */
    public static Uni<java.util.List<CloudEvent>> findBySubject(String subject) {
        return list("subject = ?1 ORDER BY time ASC", subject);
    }

    /**
     * Find unprocessed events older than now, ordered by creation time.
     * Limit the result size to avoid OOM.
     */
    public static Uni<java.util.List<CloudEvent>> findUnprocessed(int limit) {
        return find("processedAt IS NULL ORDER BY createdAt ASC").page(0, limit).list();
    }
}
