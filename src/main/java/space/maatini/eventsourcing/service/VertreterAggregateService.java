package space.maatini.eventsourcing.service;

import java.util.List;

import space.maatini.eventsourcing.dto.VertreterDTO;
import space.maatini.eventsourcing.entity.VertreterAggregate;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

/**
 * Service for querying Vertreter aggregate state.
 * This is a read-only service - the state is computed by
 * VertreterProjectorService.
 */
@ApplicationScoped
public class VertreterAggregateService {

    private final Logger log;

    public VertreterAggregateService(Logger log) {
        this.log = log;
    }

    /**
     * Get the current state of a Vertreter by ID.
     */
    @WithSession
    public Uni<VertreterDTO> findById(String id) {
        log.debugf("Finding Vertreter aggregate: id=%s", id);
        return VertreterAggregate.findByVertreterId(id)
                .map(entity -> entity != null ? VertreterDTO.from(entity) : null);
    }

    /**
     * Get all Vertreter aggregates.
     */
    @WithSession
    public Uni<List<VertreterDTO>> findAll() {
        log.debug("Finding all Vertreter aggregates");
        return VertreterAggregate.findAllOrderedByName()
                .map(list -> list.stream().map(VertreterDTO::from).toList());
    }

    /**
     * Find a Vertreter by email.
     */
    @WithSession
    public Uni<VertreterDTO> findByEmail(String email) {
        log.debugf("Finding Vertreter aggregate by email: %s", email);
        return VertreterAggregate.findByEmail(email)
                .map(entity -> entity != null ? VertreterDTO.from(entity) : null);
    }

    /**
     * Count all Vertreter aggregates.
     */
    @WithSession
    public Uni<Long> count() {
        return VertreterAggregate.count();
    }

    /**
     * Find Vertreter by represented person ID.
     */
    @WithSession
    public Uni<List<VertreterDTO>> findByVertretenePersonId(String id) {
        log.debugf("Finding Vertreter by vertretene person id: %s", id);
        return VertreterAggregate.findByVertretenePersonId(id)
                .map(list -> list.stream().map(VertreterDTO::from).toList());
    }
}
