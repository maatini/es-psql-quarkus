package space.maatini.eventsourcing.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Read-only aggregate entity for Vertreter.
 * The state is computed by the Java application (VertreterProjectorService),
 * not by DB triggers.
 */
@Entity
@Table(name = "vertreter_aggregate")
public class VertreterAggregate extends PanacheEntityBase {

    @Id
    public String id;

    public String name;

    public String email;

    @Column(name = "vertretene_person_id")
    public String vertretenePersonId;

    @Column(name = "vertretene_person_name")
    public String vertretenePersonName;

    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    @Column(name = "event_id")
    public UUID eventId;

    public Integer version;

    /**
     * Find Vertreter by ID.
     */
    public static Uni<VertreterAggregate> findByVertreterId(String id) {
        return find("id", id).firstResult();
    }

    /**
     * Find all Vertreter, ordered by name.
     */
    public static Uni<java.util.List<VertreterAggregate>> findAllOrderedByName() {
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
    public static Uni<java.util.List<VertreterAggregate>> findByVertretenePersonId(String id) {
        return list("vertretenePersonId", id);
    }
}
