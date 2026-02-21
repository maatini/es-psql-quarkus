package space.maatini.eventsourcing.resource;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.OutboxEvent;
import space.maatini.eventsourcing.entity.AggregateSnapshot;

/**
 * Test-only REST resource for database cleanup between tests.
 * This is registered automatically during @QuarkusTest runs.
 */
@Path("/test-support")
@io.quarkus.arc.profile.IfBuildProfile("test")
public class TestSupportResource {

    @POST
    @Path("/wipe")
    @WithTransaction
    public Uni<Response> wipeDatabase() {
        return CloudEvent.getSession().chain(session ->
                session.createNativeQuery("DELETE FROM events_dead_letter").executeUpdate()
        )
        .chain(() -> OutboxEvent.deleteAll())
        .chain(() -> AggregateSnapshot.deleteAll())
        .chain(() -> space.maatini.eventsourcing.entity.JsonAggregate.deleteAll())
        .chain(() -> CloudEvent.deleteAll())
        .replaceWith(Response.ok().build());
    }
}
