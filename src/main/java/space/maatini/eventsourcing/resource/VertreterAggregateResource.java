package space.maatini.eventsourcing.resource;

import space.maatini.eventsourcing.dto.ErrorResponse;
import space.maatini.eventsourcing.dto.VertreterDTO;
import space.maatini.eventsourcing.service.VertreterAggregateService;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponses;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

/**
 * REST endpoint for querying Vertreter aggregate state.
 */
@Path("/aggregates/vertreter")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Vertreter Aggregates", description = "Query current state of Vertreter aggregates")
public class VertreterAggregateResource {

    private final VertreterAggregateService vertreterService;

    public VertreterAggregateResource(VertreterAggregateService vertreterService) {
        this.vertreterService = vertreterService;
    }

    @GET
    @Operation(summary = "List all Vertreter", description = "Get all Vertreter aggregates ordered by name")
    public Uni<Response> listAll() {
        return vertreterService.findAll()
                .map(list -> Response.ok(list).build());
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get Vertreter by ID", description = "Get the current state of a Vertreter by their ID")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Vertreter found"),
            @APIResponse(responseCode = "404", description = "Vertreter not found")
    })
    public Uni<Response> getById(@PathParam("id") String id) {
        return vertreterService.findById(id)
                .map(vertreter -> {
                    if (vertreter == null) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(new ErrorResponse("Vertreter not found", "No Vertreter with ID: " + id))
                                .build();
                    }
                    return Response.ok(vertreter).build();
                });
    }

    @GET
    @Path("/email/{email}")
    @Operation(summary = "Get Vertreter by email", description = "Find a Vertreter by their email address")
    @APIResponses({
            @APIResponse(responseCode = "200", description = "Vertreter found"),
            @APIResponse(responseCode = "404", description = "Vertreter not found")
    })
    public Uni<Response> getByEmail(@PathParam("email") String email) {
        return vertreterService.findByEmail(email)
                .map(vertreter -> {
                    if (vertreter == null) {
                        return Response.status(Response.Status.NOT_FOUND)
                                .entity(new ErrorResponse("Vertreter not found", "No Vertreter with email: " + email))
                                .build();
                    }
                    return Response.ok(vertreter).build();
                });
    }

    @GET
    @Path("/count")
    @Operation(summary = "Count Vertreter", description = "Get the total number of Vertreter aggregates")
    public Uni<Response> count() {
        return vertreterService.count()
                .map(count -> Response.ok(new CountResponse(count)).build());
    }

    @GET
    @Path("/vertretene-person/{id}")
    @Operation(summary = "Get Vertreter by Vertretene Person ID", description = "Find representatives for a specific person")
    public Uni<Response> getByVertretenePersonId(@PathParam("id") String id) {
        return vertreterService.findByVertretenePersonId(id)
                .map(list -> Response.ok(list).build());
    }

    record CountResponse(long count) {
    }
}
