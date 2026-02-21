package space.maatini.eventsourcing.example.vertreter.domain;

import space.maatini.eventsourcing.domain.DomainAggregateRoot;
import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.example.vertreter.dto.command.CreateVertreterCommand;
import space.maatini.eventsourcing.example.vertreter.dto.command.UpdateVertreterCommand;
import space.maatini.eventsourcing.example.vertreter.dto.command.DeleteVertreterCommand;
import io.vertx.core.json.JsonObject;

public class Vertreter extends DomainAggregateRoot {
    private boolean deleted = false;
    private boolean created = false;

    public Vertreter(String id) {
        super(id);
    }

    public void create(CreateVertreterCommand cmd) {
        if (created) throw new IllegalStateException("Vertreter was already created");
        
        JsonObject data = new JsonObject()
            .put("id", cmd.id())
            .put("name", cmd.name());
        
        if (cmd.email() != null) data.put("email", cmd.email());
        if (cmd.vertretenePerson() != null) {
             data.put("vertretenePerson", new JsonObject()
                 .put("id", cmd.vertretenePerson().id())
                 .put("name", cmd.vertretenePerson().name()));
        }

        emitEvent("space.maatini.vertreter.created", data);
    }

    public void update(UpdateVertreterCommand cmd) {
        if (!created) throw new IllegalStateException("Vertreter does not exist or was not created yet");
        if (deleted) throw new IllegalStateException("Vertreter was deleted");

        JsonObject data = new JsonObject().put("id", cmd.id());
        if (cmd.name() != null) data.put("name", cmd.name());
        if (cmd.email() != null) data.put("email", cmd.email());
        if (cmd.vertretenePerson() != null) {
             data.put("vertretenePerson", new JsonObject()
                 .put("id", cmd.vertretenePerson().id())
                 .put("name", cmd.vertretenePerson().name()));
        }

        emitEvent("space.maatini.vertreter.updated", data);
    }

    public void delete(DeleteVertreterCommand cmd) {
        if (!created) throw new IllegalStateException("Vertreter does not exist");
        if (deleted) throw new IllegalStateException("Vertreter was already deleted");

        JsonObject data = new JsonObject().put("id", cmd.id());
        emitEvent("space.maatini.vertreter.deleted", data);
    }

    @Override
    protected void mutate(CloudEvent event) {
        switch (event.getType()) {
            case "space.maatini.vertreter.created":
                this.created = true;
                break;
            case "space.maatini.vertreter.deleted":
                this.deleted = true;
                break;
        }
    }
}
