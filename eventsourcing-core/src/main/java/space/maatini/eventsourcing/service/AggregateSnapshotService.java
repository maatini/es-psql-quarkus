package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import space.maatini.eventsourcing.domain.DomainAggregateRoot;
import space.maatini.eventsourcing.entity.AggregateSnapshot;

import java.util.UUID;

@ApplicationScoped
public class AggregateSnapshotService {

    /**
     * Retrieves the latest snapshot for the given aggregate.
     */
    public Uni<AggregateSnapshot> getLatestSnapshot(String aggregateId, String aggregateType) {
        return AggregateSnapshot.<AggregateSnapshot>find("aggregateId = ?1 AND aggregateType = ?2 ORDER BY aggregateVersion DESC", aggregateId, aggregateType)
                .firstResult();
    }

    /**
     * Saves a snapshot of the aggregate.
     */
    public Uni<Void> saveSnapshot(DomainAggregateRoot aggregate, UUID lastEventId) {
        AggregateSnapshot snapshot = new AggregateSnapshot();
        snapshot.aggregateId = aggregate.getId();
        snapshot.aggregateType = aggregate.getClass().getSimpleName();
        snapshot.aggregateVersion = aggregate.getVersion();
        snapshot.lastEventId = lastEventId;
        snapshot.state = aggregate.takeSnapshot().getMap();

        return snapshot.persist().replaceWithVoid();
    }
}
