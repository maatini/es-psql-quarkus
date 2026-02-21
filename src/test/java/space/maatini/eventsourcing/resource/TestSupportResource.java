package space.maatini.eventsourcing.resource;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import space.maatini.eventsourcing.entity.CloudEvent;

@Path("/test-support")
public class TestSupportResource {

    @POST
    @Path("/wipe")
    @WithTransaction
    public Uni<Response> wipeDatabase() {
        return CloudEvent.getSession().chain(session ->
                session.createNativeQuery("DELETE FROM events_dead_letter").executeUpdate()
        )
        .chain(() -> space.maatini.eventsourcing.entity.JsonAggregate.deleteAll())
        .chain(() -> CloudEvent.deleteAll())
        .replaceWith(Response.ok().build());
    }
}
