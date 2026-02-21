package space.maatini.eventsourcing.example.vertreter.dto.command;

public record CreateVertreterCommand(
    String id,
    String name,
    String email,
    VertretenePersonCommandDTO vertretenePerson
) {}
