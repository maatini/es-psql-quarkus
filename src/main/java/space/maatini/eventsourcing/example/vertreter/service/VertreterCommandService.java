package space.maatini.eventsourcing.example.vertreter.service;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import space.maatini.eventsourcing.example.vertreter.domain.Vertreter;
import space.maatini.eventsourcing.example.vertreter.dto.command.CreateVertreterCommand;
import space.maatini.eventsourcing.example.vertreter.dto.command.UpdateVertreterCommand;
import space.maatini.eventsourcing.entity.CloudEvent;

import java.time.OffsetDateTime;
import java.util.UUID;

@ApplicationScoped
public class VertreterCommandService {

    @WithTransaction
    public Uni<Void> createVertreter(CreateVertreterCommand cmd) {
        return loadAggregate(cmd.id()).chain(vertreter -> {
            vertreter.create(cmd);
            return saveEvents(vertreter);
        });
    }

    @WithTransaction
    public Uni<Void> updateVertreter(UpdateVertreterCommand cmd) {
        return loadAggregate(cmd.id()).chain(vertreter -> {
            vertreter.update(cmd);
            return saveEvents(vertreter);
        });
    }
    
    @WithTransaction
    public Uni<Void> deleteVertreter(String id) {
        return loadAggregate(id).chain(vertreter -> {
            vertreter.delete();
            return saveEvents(vertreter);
        });
    }

    private Uni<Vertreter> loadAggregate(String id) {
        return CloudEvent.<CloudEvent>find("subject = ?1 ORDER BY createdAt ASC", id).list()
                .map(events -> {
                    Vertreter v = new Vertreter(id);
                    events.forEach(v::apply);
                    return v;
                });
    }

    private Uni<Void> saveEvents(Vertreter vertreter) {
        if (vertreter.getUncommittedEvents().isEmpty()) {
            return Uni.createFrom().voidItem();
        }
        return Multi.createFrom().iterable(vertreter.getUncommittedEvents())
                .onItem().transformToUniAndConcatenate(event -> event.persist())
                .collect().last().replaceWithVoid();
    }
}
