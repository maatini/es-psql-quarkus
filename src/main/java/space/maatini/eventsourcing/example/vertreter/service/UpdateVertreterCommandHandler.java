package space.maatini.eventsourcing.example.vertreter.service;

import io.smallrye.mutiny.Uni;
import space.maatini.eventsourcing.command.CommandHandler;
import space.maatini.eventsourcing.command.HandlesCommand;
import space.maatini.eventsourcing.example.vertreter.domain.Vertreter;
import space.maatini.eventsourcing.example.vertreter.dto.command.UpdateVertreterCommand;

@HandlesCommand(UpdateVertreterCommand.class)
public class UpdateVertreterCommandHandler implements CommandHandler<Vertreter, UpdateVertreterCommand> {
    @Override
    public Uni<Vertreter> handle(Vertreter aggregate, UpdateVertreterCommand command) {
        aggregate.update(command);
        return Uni.createFrom().item(aggregate);
    }
}
