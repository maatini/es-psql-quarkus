package space.maatini.eventsourcing.resource;

import java.net.URI;
import java.util.UUID;

import space.maatini.eventsourcing.dto.CloudEventDTO;
import space.maatini.eventsourcing.service.EventService;
import io.smallrye.mutiny.Uni;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.RequestBody;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;

/**
 * REST endpoint for CloudEvents ingestion.
 */
@Path("/events")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Events", description = "CloudEvents ingestion API")
@RolesAllowed("admin")
public class EventResource {

    private final EventService eventService;

    public EventResource(EventService eventService) {
        this.eventService = eventService;
    }

    @POST
    @Operation(summary = "Ingest a CloudEvent", description = "Store a new CloudEvent. Idempotent - duplicate events are accepted but not re-processed.")
    @RequestBody(description = "CloudEvent to store", required = true, content = @Content(schema = @Schema(implementation = CloudEventDTO.class)))
    @APIResponses({
            @APIResponse(responseCode = "201", description = "Event created"),
            @APIResponse(responseCode = "200", description = "Event already exists (idempotent)"),
            @APIResponse(responseCode = "400", description = "Invalid event payload")
    })
    public Uni<Response> createEvent(@Valid CloudEventDTO event) {
        return eventService.storeEvent(event)
                .map(result -> {
                    if (result.alreadyExisted()) {
                        // Return 200 OK for idempotent duplicate
                        return Response.ok(result.event()).build();
                    }
                    // Return 201 Created for new event
                    return Response.created(URI.create("/events/" + result.event().getId()))
                            .entity(result.event())
                            .build();
                });
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get event by ID", description = "Retrieve a single CloudEvent by its ID")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Event found"),
            @APIResponse(responseCode = "404", description = "Event not found")
    })
    public Uni<Response> getEvent(@PathParam("id") UUID id) {
        return eventService.findById(id)
                .map(event -> {
                    if (event == null) {
                        return Response.status(Response.Status.NOT_FOUND).build();
                    }
                    return Response.ok(event).build();
                });
    }

    @GET
    @Path("/subject/{subject}")
    @Operation(summary = "Get events by subject", description = "Retrieve all events for a given subject/aggregate ID")
    public Uni<Response> getEventsBySubject(@PathParam("subject") String subject) {
        return eventService.findBySubject(subject)
                .map(events -> Response.ok(events).build());
    }

    @GET
    @Path("/type/{type}")
    @Operation(summary = "Get events by type", description = "Retrieve all events of a given type")
    public Uni<Response> getEventsByType(@PathParam("type") String type) {
        return eventService.findByType(type)
                .map(events -> Response.ok(events).build());
    }
}
