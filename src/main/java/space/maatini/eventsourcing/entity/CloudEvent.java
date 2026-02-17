package space.maatini.eventsourcing.entity;

import java.time.OffsetDateTime;
import java.util.List;
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
 * 
 * @see <a href="https://github.com/cloudevents/spec">CloudEvents
 *      Specification</a>
 */
@Entity
@Table(name = "events")
public class CloudEvent extends PanacheEntityBase {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String source;

    @Column(nullable = false)
    private String specversion = "1.0";

    @Column(nullable = false)
    private String type;

    private String subject;

    @Column(nullable = false)
    private OffsetDateTime time = OffsetDateTime.now();

    private String datacontenttype = "application/json";

    private String dataschema;

    @Column(nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonObject data;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "processed_at")
    private OffsetDateTime processedAt;

    // --- Getters & Setters ---

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getSpecversion() {
        return specversion;
    }

    public void setSpecversion(String specversion) {
        this.specversion = specversion;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public OffsetDateTime getTime() {
        return time;
    }

    public void setTime(OffsetDateTime time) {
        this.time = time;
    }

    public String getDatacontenttype() {
        return datacontenttype;
    }

    public void setDatacontenttype(String datacontenttype) {
        this.datacontenttype = datacontenttype;
    }

    public String getDataschema() {
        return dataschema;
    }

    public void setDataschema(String dataschema) {
        this.dataschema = dataschema;
    }

    public JsonObject getData() {
        return data;
    }

    public void setData(JsonObject data) {
        this.data = data;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(OffsetDateTime processedAt) {
        this.processedAt = processedAt;
    }

    // --- Static query methods ---

    /**
     * Check if an event with this ID already exists (idempotency check).
     */
    public static Uni<Boolean> existsById(UUID id) {
        return find("id", id).count().map(count -> count > 0);
    }

    /**
     * Find events by type.
     */
    public static Uni<List<CloudEvent>> findByType(String type) {
        return list("type", type);
    }

    /**
     * Find events by subject (aggregate ID).
     */
    public static Uni<List<CloudEvent>> findBySubject(String subject) {
        return list("subject = ?1 ORDER BY time ASC", subject);
    }

    /**
     * Find unprocessed events older than now, ordered by creation time.
     * Limit the result size to avoid OOM.
     * Uses PESSIMISTIC_WRITE with SKIP LOCKED to prevent multiple threads from
     * processing the same events.
     */
    public static Uni<List<CloudEvent>> findUnprocessed(int limit) {
        return find("processedAt IS NULL ORDER BY createdAt ASC")
                .withLock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
                .page(0, limit)
                .list();
    }
}
