package de.antigravity.eventsourcing.resource;

import de.antigravity.eventsourcing.service.VertreterProjectorService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

@Path("/admin")
@Tag(name = "Admin", description = "Administration endpoints")
public class AdminResource {

    @Inject
    VertreterProjectorService projectorService;

    @POST
    @Path("/projection/trigger")
    @Produces(MediaType.APPLICATION_JSON)
    @Operation(summary = "Trigger projection", description = "Manually trigger the event projection process")
    public Uni<Response> triggerProjection() {
        return projectorService.processBatch()
            .map(count -> Response.ok().entity("{\"processed\": " + count + "}").build());
    }
}
