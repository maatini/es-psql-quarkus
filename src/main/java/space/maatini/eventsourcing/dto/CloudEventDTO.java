package space.maatini.eventsourcing.dto;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * DTO for receiving CloudEvents via REST API.
 */
@Schema(description = "CloudEvents-compliant event payload")
public record CloudEventDTO(
        @Schema(description = "Unique event identifier", example = "550e8400-e29b-41d4-a716-446655440000") @NotNull(message = "Event ID is required") UUID id,

        @Schema(description = "Event source URI", example = "/vertreter-service") @NotBlank(message = "Source is required") String source,

        @Schema(description = "CloudEvents specification version", defaultValue = "1.0") String specversion,

        @Schema(description = "Event type", example = "space.maatini.vertreter.updated") @NotBlank(message = "Event type is required") String type,

        @Schema(description = "Subject/aggregate ID", example = "v001") String subject,

        @Schema(description = "Event timestamp") OffsetDateTime time,

        @Schema(description = "Content type of data", defaultValue = "application/json") String datacontenttype,

        @Schema(description = "Data schema URI") String dataschema,

        @Schema(description = "Version of the data schema", defaultValue = "1") Integer dataVersion,

        @Schema(description = "Event payload", example = "{\"id\": \"v001\", \"name\": \"Max Mustermann\", \"email\": \"max@example.com\"}") @NotNull(message = "Event data is required") Map<String, Object> data) {
    /**
     * Apply defaults for optional fields.
     */
    public CloudEventDTO withDefaults() {
        return new CloudEventDTO(
                id,
                source,
                specversion != null ? specversion : "1.0",
                type,
                subject,
                time != null ? time : OffsetDateTime.now(),
                datacontenttype != null ? datacontenttype : "application/json",
                dataschema,
                dataVersion != null ? dataVersion : 1,
                data);
    }
}
