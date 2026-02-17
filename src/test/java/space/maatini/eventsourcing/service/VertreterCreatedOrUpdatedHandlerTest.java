package space.maatini.eventsourcing.service;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import space.maatini.eventsourcing.entity.CloudEvent;
import space.maatini.eventsourcing.entity.VertreterAggregate;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class VertreterCreatedOrUpdatedHandlerTest {

    @Inject
    VertreterCreatedOrUpdatedHandler handler;

    @Inject
    Vertx vertx;

    @BeforeEach
    void cleanup() {
        runInVertx(() -> Panache.withTransaction(() -> VertreterAggregate.deleteAll()));
    }

    @Test
    @DisplayName("Create new aggregate from .created event")
    void createNewAggregate() {
        String id = "create-test-" + UUID.randomUUID();

        CloudEvent event = createEvent("space.maatini.vertreter.created", id, Map.of(
                "id", id,
                "name", "Max Mustermann",
                "email", "max@example.com"));

        runInVertx(() -> Panache.withTransaction(() -> handler.handle(event)));

        VertreterAggregate agg = runInVertx(() -> Panache.withSession(() -> VertreterAggregate.findById(id)));

        assertNotNull(agg);
        assertEquals("Max Mustermann", agg.getName());
        assertEquals("max@example.com", agg.getEmail());
        assertEquals(1, agg.getVersion());
    }

    @Test
    @DisplayName("Partial update - only name changes, email preserved")
    void partialUpdate_nameOnly() {
        String id = "partial-name-" + UUID.randomUUID();
        createAggregate(id, "Old Name", "old@example.com");

        CloudEvent event = createEvent("space.maatini.vertreter.updated", id, Map.of(
                "id", id,
                "name", "New Name"));

        runInVertx(() -> Panache.withTransaction(() -> handler.handle(event)));

        VertreterAggregate agg = runInVertx(() -> Panache.withSession(() -> VertreterAggregate.findById(id)));

        assertEquals("New Name", agg.getName());
        assertEquals("old@example.com", agg.getEmail()); // preserved
        assertEquals(2, agg.getVersion());
    }

    @Test
    @DisplayName("Partial update - only email changes")
    void partialUpdate_emailOnly() {
        String id = "partial-email-" + UUID.randomUUID();
        createAggregate(id, "Name", "old@example.com");

        CloudEvent event = createEvent("space.maatini.vertreter.updated", id, Map.of(
                "id", id,
                "email", "new@example.com"));

        runInVertx(() -> Panache.withTransaction(() -> handler.handle(event)));

        VertreterAggregate agg = runInVertx(() -> Panache.withSession(() -> VertreterAggregate.findById(id)));

        assertEquals("Name", agg.getName());
        assertEquals("new@example.com", agg.getEmail());
        assertEquals(2, agg.getVersion());
    }

    @Test
    @DisplayName("Update vertretenePerson (add / change)")
    void updateVertretenePerson() {
        String id = "vp-update-" + UUID.randomUUID();
        createAggregate(id, "Name", "email@example.com");

        CloudEvent event = createEvent("space.maatini.vertreter.updated", id, Map.of(
                "id", id,
                "vertretenePerson", Map.of("id", "p001", "name", "Erika Musterfrau")));

        runInVertx(() -> Panache.withTransaction(() -> handler.handle(event)));

        VertreterAggregate agg = runInVertx(() -> Panache.withSession(() -> VertreterAggregate.findById(id)));

        assertEquals("p001", agg.getVertretenePersonId());
        assertEquals("Erika Musterfrau", agg.getVertretenePersonName());
    }

    @Test
    @DisplayName("Missing data.id → event is ignored (no crash)")
    void missingIdInData_ignored() {
        CloudEvent event = createEvent("space.maatini.vertreter.updated", null, Map.of(
                "name", "No ID"));

        assertDoesNotThrow(() -> runInVertx(() -> Panache.withTransaction(() -> handler.handle(event))));
    }

    @Test
    @DisplayName("Update event on non-existing aggregate acts as upsert")
    void updateOnNonExisting_isUpsert() {
        String id = "upsert-" + UUID.randomUUID();
        CloudEvent event = createEvent("space.maatini.vertreter.updated", id, Map.of(
                "id", id,
                "name", "Born from Update",
                "email", "upsert@test.com"));

        runInVertx(() -> Panache.withTransaction(() -> handler.handle(event)));

        VertreterAggregate agg = runInVertx(() -> Panache.withSession(() -> VertreterAggregate.findById(id)));

        assertNotNull(agg);
        assertEquals("Born from Update", agg.getName());
    }

    private CloudEvent createEvent(String type, String subject, Map<String, Object> data) {
        CloudEvent e = new CloudEvent();
        e.setId(UUID.randomUUID());
        e.setType(type);
        e.setSubject(subject);
        e.setTime(OffsetDateTime.now());
        e.setData(new io.vertx.core.json.JsonObject(data));
        return e;
    }

    private void createAggregate(String id, String name, String email) {
        runInVertx(() -> Panache.withTransaction(() -> {
            VertreterAggregate agg = new VertreterAggregate();
            agg.setId(id);
            agg.setName(name);
            agg.setEmail(email);
            agg.setVersion(1);
            return agg.persist();
        }));
    }

    private <T> T runInVertx(Supplier<Uni<T>> supplier) {
        CompletableFuture<T> future = new CompletableFuture<>();
        // Hibernate Reactive requires a duplicated context flagged as "safe"
        io.vertx.core.impl.ContextInternal context = ((io.vertx.core.impl.ContextInternal) vertx.getOrCreateContext())
                .duplicate();
        VertxContextSafetyToggle.setContextSafe(context, true);

        context.runOnContext(v -> {
            try {
                supplier.get().subscribe().with(
                        item -> {
                            future.complete(item);
                        },
                        fail -> {
                            future.completeExceptionally(fail);
                        });
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            if (e.getCause() != null) {
                throw new RuntimeException(e.getCause());
            }
            throw new RuntimeException(e);
        }
    }
}
