package de.antigravity.eventsourcing.dto;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Response DTO for Vertreter aggregate state.
 */
@Schema(description = "Current state of a Vertreter aggregate")
public record VertreterDTO(
    @Schema(description = "Vertreter ID", example = "v001")
    String id,
    
    @Schema(description = "Vertreter name", example = "Max Mustermann")
    String name,
    
    @Schema(description = "Vertreter email", example = "max@example.com")
    String email,
    
    @Schema(description = "Represented person info")
    VertretenePersonDTO vertretenePerson,
    
    @Schema(description = "Last update timestamp")
    OffsetDateTime updatedAt,
    
    @Schema(description = "ID of the last event that updated this state")
    UUID lastEventId,
    
    @Schema(description = "Aggregate version number")
    Integer version
) {
    public static VertreterDTO from(de.antigravity.eventsourcing.entity.VertreterAggregate entity) {
        VertretenePersonDTO vertretenePerson = null;
        if (entity.vertretenePersonId != null) {
            vertretenePerson = new VertretenePersonDTO(
                entity.vertretenePersonId,
                entity.vertretenePersonName
            );
        }
        
        return new VertreterDTO(
            entity.id,
            entity.name,
            entity.email,
            vertretenePerson,
            entity.updatedAt,
            entity.eventId,
            entity.version
        );
    }
    
    /**
     * Nested DTO for the represented person.
     */
    @Schema(description = "Information about the represented person")
    public record VertretenePersonDTO(
        @Schema(description = "ID of the represented person", example = "p001")
        String id,
        
        @Schema(description = "Name of the represented person", example = "Erika Musterfrau")
        String name
    ) {}
}
