package space.maatini.eventsourcing.example.vertreter.dto.command;

public record UpdateVertreterCommand(
    String id,
    String name,
    String email,
    VertretenePersonCommandDTO vertretenePerson
) {}
