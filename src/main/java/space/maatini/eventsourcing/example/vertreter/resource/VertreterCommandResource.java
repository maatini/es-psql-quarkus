package space.maatini.eventsourcing.example.vertreter.resource;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import space.maatini.eventsourcing.example.vertreter.dto.command.CreateVertreterCommand;
import space.maatini.eventsourcing.example.vertreter.dto.command.UpdateVertreterCommand;
import space.maatini.eventsourcing.command.CommandBus;
import space.maatini.eventsourcing.example.vertreter.domain.Vertreter;
import space.maatini.eventsourcing.example.vertreter.dto.command.DeleteVertreterCommand;

@Path("/commands/vertreter")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Command Resource", description = "Endpoints for executing business commands on Vertreter aggregates")
public class VertreterCommandResource {

    private final CommandBus commandBus;

    public VertreterCommandResource(CommandBus commandBus) {
        this.commandBus = commandBus;
    }

    @POST
    @Operation(summary = "Create a new Vertreter")
    public Uni<Response> create(CreateVertreterCommand command) {
        if (command == null || command.id() == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Missing command ID").build());
        }
        return commandBus.dispatch(command.id(), Vertreter.class, command)
                .map(v -> Response.status(Response.Status.CREATED).build())
                .onFailure(IllegalStateException.class).recoverWithItem(e -> 
                        Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Update an existing Vertreter")
    public Uni<Response> update(@PathParam("id") String id, UpdateVertreterCommand command) {
        if (command == null) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("Missing command payload").build());
        }
        if (!id.equals(command.id())) {
            return Uni.createFrom().item(Response.status(Response.Status.BAD_REQUEST).entity("ID mismatch").build());
        }
        return commandBus.dispatch(command.id(), Vertreter.class, command)
                .map(v -> Response.ok().build())
                .onFailure(IllegalStateException.class).recoverWithItem(e -> 
                        Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
    }

    @DELETE
    @Path("/{id}")
    @Operation(summary = "Delete an existing Vertreter")
    public Uni<Response> delete(@PathParam("id") String id) {
        DeleteVertreterCommand command = new DeleteVertreterCommand(id);
        return commandBus.dispatch(id, Vertreter.class, command)
                .map(v -> Response.ok().build())
                .onFailure(IllegalStateException.class).recoverWithItem(e -> 
                        Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build());
    }
}
