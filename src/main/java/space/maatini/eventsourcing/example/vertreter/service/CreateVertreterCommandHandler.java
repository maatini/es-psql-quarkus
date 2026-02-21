package space.maatini.eventsourcing.example.vertreter.service;

import io.smallrye.mutiny.Uni;
import space.maatini.eventsourcing.command.CommandHandler;
import space.maatini.eventsourcing.command.HandlesCommand;
import space.maatini.eventsourcing.example.vertreter.domain.Vertreter;
import space.maatini.eventsourcing.example.vertreter.dto.command.CreateVertreterCommand;

@HandlesCommand(CreateVertreterCommand.class)
public class CreateVertreterCommandHandler implements CommandHandler<Vertreter, CreateVertreterCommand> {
    @Override
    public Uni<Vertreter> handle(Vertreter aggregate, CreateVertreterCommand command) {
        aggregate.create(command);
        return Uni.createFrom().item(aggregate);
    }
}
