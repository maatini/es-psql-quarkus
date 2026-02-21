package space.maatini.eventsourcing.resource;

import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import space.maatini.eventsourcing.service.GenericAggregateService;

@Path("/aggregates")
@Produces(MediaType.APPLICATION_JSON)
@Tag(name = "Generic Aggregates")
public class GenericAggregateResource {

    @Inject
    GenericAggregateService service;

    @GET
    @Path("/{type}/{id}")
    public Uni<Response> get(@PathParam("type") String type, @PathParam("id") String id) {
        return service.findByTypeAndId(type, id)
                .map(json -> json != null 
                    ? Response.ok(json).build() 
                    : Response.status(404).build());
    }

    @GET
    @Path("/{type}")
    public Uni<Response> list(@PathParam("type") String type) {
        // Hinweis: In einer vollen Implementierung könnte hier QueryParam für Filtering (JSONPath) genutzt werden
        return service.listByType(type)
                .map(list -> Response.ok(list).build());
    }
}
