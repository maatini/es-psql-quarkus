package space.maatini.eventsourcing.entity;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only aggregate entity for Vertreter.
 * The state is computed by the Java application (ProjectionService),
 * not by DB triggers.
 */
@Entity
@Table(name = "vertreter_aggregate")
public class VertreterAggregate extends PanacheEntityBase implements AggregateRoot {

    @Id
    private String id;

    private String name;

    private String email;

    @Column(name = "vertretene_person_id")
    private String vertretenePersonId;

    @Column(name = "vertretene_person_name")
    private String vertretenePersonName;

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @Column(name = "event_id")
    private UUID eventId;

    private Integer version;

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getVertretenePersonId() {
        return vertretenePersonId;
    }

    public void setVertretenePersonId(String vertretenePersonId) {
        this.vertretenePersonId = vertretenePersonId;
    }

    public String getVertretenePersonName() {
        return vertretenePersonName;
    }

    public void setVertretenePersonName(String vertretenePersonName) {
        this.vertretenePersonName = vertretenePersonName;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    // --- Static query methods ---

    /**
     * Find Vertreter by ID.
     */
    public static Uni<VertreterAggregate> findByVertreterId(String id) {
        return find("id", id).firstResult();
    }

    /**
     * Find all Vertreter, ordered by name.
     */
    public static Uni<List<VertreterAggregate>> findAllOrderedByName() {
        return list("ORDER BY name ASC");
    }

    /**
     * Find Vertreter by email.
     */
    public static Uni<VertreterAggregate> findByEmail(String email) {
        return find("email", email).firstResult();
    }

    /**
     * Find Vertreter by vertretene person ID.
     */
    public static Uni<List<VertreterAggregate>> findByVertretenePersonId(String id) {
        return list("vertretenePersonId", id);
    }
}
