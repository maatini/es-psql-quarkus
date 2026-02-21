package space.maatini.eventsourcing.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.vertx.core.json.JsonObject;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.Matchers.hasItem;

@QuarkusTest
@io.quarkus.test.junit.TestProfile(space.maatini.eventsourcing.TestProfile.class)

public class GenericAggregateResourceTest {

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        given()
            .post("/test-support/wipe")
            .then()
            .statusCode(200);
    }

    @Test
    public void testGenericAggregateWorkflow() {
        String id = "gen-" + UUID.randomUUID().toString().substring(0, 8);
        String name = "Generic Test Vertreter";
        String email = id + "@test.com";

        // 1. Create a Vertreter via Event (simulating projection)
        JsonObject eventData = new JsonObject()
                .put("id", id)
                .put("name", name)
                .put("email", email);

        JsonObject event = new JsonObject()
                .put("id", UUID.randomUUID().toString())
                .put("type", "space.maatini.vertreter.created")
                .put("subject", id)
                .put("aggregateVersion", 1)
                .put("data", eventData)
                .put("source", "/tests");

        given()
                .contentType("application/json")
                .body(event.encode())
                .when()
                .post("/events")
                .then()
                .statusCode(201);


        // 2. Wait for projection
        awaitProjection();

        // 3. Verify via Generic Resource
        given()
                .when()
                .get("/aggregates/vertreter/" + id)
                .then()
                .statusCode(200)
                .body("name", is(name))
                .body("email", is(email));

        // 4. Verify listing
        given()
                .when()
                .get("/aggregates/vertreter")
                .then()
                .statusCode(200)
                .body("name", hasItem(name));
    }

    private void awaitProjection() {
        for (int i = 0; i < 100; i++) {
            int processed = given()
                    .post("/admin/projection/trigger")
                    .then()
                    .statusCode(200)
                    .extract().path("processed");
            if (processed == 0) break;
        }
    }
}
