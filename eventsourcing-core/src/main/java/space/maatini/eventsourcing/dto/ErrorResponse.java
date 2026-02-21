package space.maatini.eventsourcing.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * Shared error response DTO used across exception mappers and resources.
 */
@Schema(description = "Error response payload")
public record ErrorResponse(
        @Schema(description = "Error category") String error,
        @Schema(description = "Detailed error message") String message) {
}
