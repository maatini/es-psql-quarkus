package space.maatini.eventsourcing.dto.command;

public record CreateVertreterCommand(
    String id,
    String name,
    String email,
    VertretenePersonCommandDTO vertretenePerson
) {}
