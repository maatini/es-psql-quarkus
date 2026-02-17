package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.VertreterAggregate;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Handler für das Löschen eines Vertreters.
 */
@ApplicationScoped
public class VertreterDeletedHandler implements VertreterEventHandler {

    private static final String DELETED = "space.maatini.vertreter.deleted";

    @Override
    public boolean canHandle(String eventType) {
        return DELETED.equals(eventType);
    }

    @Override
    public Uni<Void> handle(CloudEvent event) {
        String id = event.getData().getString("id");
        return VertreterAggregate.deleteById(id).replaceWithVoid();
    }
}
