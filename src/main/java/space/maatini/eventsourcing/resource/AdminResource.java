package space.maatini.eventsourcing.resource;

import space.maatini.eventsourcing.dto.ErrorResponse;
import space.maatini.eventsourcing.service.VertreterProjectorService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.UUID;

/**
 * Administration endpoints for the event sourcing system.
 */
@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Admin", description = "Administration and maintenance endpoints")
public class AdminResource {

    private final VertreterProjectorService projectorService;

    public AdminResource(VertreterProjectorService projectorService) {
        this.projectorService = projectorService;
    }

    @POST
    @Path("/projection/trigger")
    @Operation(summary = "Manually trigger projection", description = "Triggers the processing of unprocessed events (useful for testing or after manual DB changes)")
    public Uni<Response> triggerProjection() {
        return projectorService.triggerManualBatch()
                .map(count -> Response.ok(new ProjectionResult(count)).build());
    }

    @POST
    @Path("/replay")
    @Operation(summary = "Replay all events", description = "Deletes all aggregates and re-processes every event from the beginning (full replay). "
            +
            "Optionally start from a specific event ID.")
    public Uni<Response> replayAll(@QueryParam("fromEventId") UUID fromEventId) {
        return projectorService.replayAll(fromEventId)
                .map(count -> Response.ok(new ReplayResult(count)).build())
                .onFailure().recoverWithItem(failure -> Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                        .entity(new ErrorResponse("Replay failed", failure.getMessage()))
                        .build());
    }

    // ==================== Inner Result Records ====================

    /**
     * Result for manual projection trigger.
     */
    public record ProjectionResult(int processed) {
    }

    /**
     * Result for full replay.
     */
    public record ReplayResult(int eventsReplayed) {
    }
}
