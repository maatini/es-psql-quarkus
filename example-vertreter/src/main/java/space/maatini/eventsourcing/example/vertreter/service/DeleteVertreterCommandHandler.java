package space.maatini.eventsourcing.example.vertreter.service;

import io.smallrye.mutiny.Uni;
import space.maatini.eventsourcing.command.CommandHandler;
import space.maatini.eventsourcing.command.HandlesCommand;
import space.maatini.eventsourcing.example.vertreter.domain.Vertreter;
import space.maatini.eventsourcing.example.vertreter.dto.command.DeleteVertreterCommand;

@HandlesCommand(DeleteVertreterCommand.class)
public class DeleteVertreterCommandHandler implements CommandHandler<Vertreter, DeleteVertreterCommand> {
    @Override
    public Uni<Vertreter> handle(Vertreter aggregate, DeleteVertreterCommand command) {
        aggregate.delete(command);
        return Uni.createFrom().item(aggregate);
    }
}
