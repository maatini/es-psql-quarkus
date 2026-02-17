package space.maatini.eventsourcing.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import space.maatini.eventsourcing.entity.VertreterAggregate;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for Vertreter aggregate state.
 */
@Schema(description = "Current state of a Vertreter aggregate")
public record VertreterDTO(
        @Schema(description = "Vertreter ID", example = "v001") String id,

        @Schema(description = "Vertreter name", example = "Max Mustermann") String name,

        @Schema(description = "Vertreter email", example = "max@example.com") String email,

        @Schema(description = "Represented person info") VertretenePersonDTO vertretenePerson,

        @Schema(description = "Last update timestamp") OffsetDateTime updatedAt,

        @Schema(description = "ID of the last event that updated this state") UUID lastEventId,

        @Schema(description = "Aggregate version number") Integer version) {
    public static VertreterDTO from(VertreterAggregate entity) {
        VertretenePersonDTO vertretenePerson = null;
        if (entity.getVertretenePersonId() != null) {
            vertretenePerson = new VertretenePersonDTO(
                    entity.getVertretenePersonId(),
                    entity.getVertretenePersonName());
        }

        return new VertreterDTO(
                entity.getId(),
                entity.getName(),
                entity.getEmail(),
                vertretenePerson,
                entity.getUpdatedAt(),
                entity.getEventId(),
                entity.getVersion());
    }

    /**
     * Nested DTO for the represented person.
     */
    @Schema(description = "Information about the represented person")
    public record VertretenePersonDTO(
            @Schema(description = "ID of the represented person", example = "p001") String id,

            @Schema(description = "Name of the represented person", example = "Erika Musterfrau") String name) {
    }
}
