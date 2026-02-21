package space.maatini.eventsourcing.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "aggregate_snapshots")
public class AggregateSnapshot extends PanacheEntityBase {

    @Id
    @Column(name = "aggregate_id")
    public String aggregateId;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_version", nullable = false)
    public Integer aggregateVersion = 0;

    @Column(name = "last_event_id", nullable = false)
    public UUID lastEventId;

    @Column(name = "state", nullable = false, columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, Object> state = new HashMap<>();

    @Column(name = "created_at")
    public OffsetDateTime createdAt = OffsetDateTime.now();
    
    public AggregateSnapshot() {}
}
