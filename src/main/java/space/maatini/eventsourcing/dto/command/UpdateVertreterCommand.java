package space.maatini.eventsourcing.dto.command;

public record UpdateVertreterCommand(
    String id,
    String name,
    String email,
    VertretenePersonCommandDTO vertretenePerson
) {}
