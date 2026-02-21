package space.maatini.eventsourcing.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Integration tests for AdminResource REST endpoints.
 * Tests cover projection trigger, replay, and replay with fromEventId.
 *
 * IMPORTANT: Replay tests delete all aggregates and reset processedAt on all
 * events.
 * After replay, they drain ALL unprocessed events to rebuild aggregates for
 * other tests.
 */
@QuarkusTest
class AdminResourceTest {

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        io.restassured.RestAssured.given()
            .post("/test-support/wipe")
            .then()
            .statusCode(200);
    }

    private static final String ADMIN_PATH = "/admin";
    private static final String EVENTS_PATH = "/events";
    private static final String AGGREGATES_PATH = "/aggregates/vertreter";

    /**
     * Drain all unprocessed events by calling trigger repeatedly until 0.
     */
    private void drainAllEvents() {
        for (int i = 0; i < 1000; i++) {
            int processed = given()
                    .post(ADMIN_PATH + "/projection/trigger")
                    .then()
                    .statusCode(200)
                    .extract().path("processed");
            if (processed == 0)
                break;
        }
    }

    @Test
    @DisplayName("POST /admin/projection/trigger - Returns 200 with processed count")
    void triggerProjection_returns200() {
        // First, create an event so there's something to process
        String vertreterId = "admin-trigger-" + UUID.randomUUID().toString().substring(0, 8);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "data": {"id": "%s", "name": "Admin Trigger Test", "email": "admin-trigger@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        // Trigger projection
        given()
                .when()
                .post(ADMIN_PATH + "/projection/trigger")
                .then()
                .statusCode(200)
                .body("processed", greaterThanOrEqualTo(0));

        // Drain remaining
        drainAllEvents();
    }

    @Test
    @DisplayName("POST /admin/projection/trigger - Returns 0 when no unprocessed events")
    void triggerProjection_noEvents_returnsZero() {
        // Drain any pending first
        drainAllEvents();

        // Now should find 0
        given()
                .when()
                .post(ADMIN_PATH + "/projection/trigger")
                .then()
                .statusCode(200)
                .body("processed", equalTo(0));
    }

    @Test
    @DisplayName("POST /admin/replay - Replays all events and returns count")
    void replayAll_returns200() {
        // Create some test data first
        String vertreterId = "replay-test-" + UUID.randomUUID().toString().substring(0, 8);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "data": {"id": "%s", "name": "Replay Test", "email": "replay@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        drainAllEvents();

        // Verify aggregate exists
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200);

        // Replay all events
        given()
                .when()
                .post(ADMIN_PATH + "/replay")
                .then()
                .statusCode(200)
                .body("eventsReplayed", greaterThanOrEqualTo(1));

        // IMPORTANT: drain ALL events to rebuild aggregates for other test classes
        drainAllEvents();
    }

    @Test
    @DisplayName("POST /admin/replay - Side effect: aggregates are rebuilt after replay + trigger")
    void replayAll_rebuildsSideEffects() {
        // Create and project an event
        String vertreterId = "replay-side-" + UUID.randomUUID().toString().substring(0, 8);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "data": {"id": "%s", "name": "Side Effect Test", "email": "side@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        drainAllEvents();
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200);

        // Replay → deletes aggregates, resets processedAt
        given().post(ADMIN_PATH + "/replay").then().statusCode(200);

        // Immediately after replay (before trigger), aggregate is gone
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(404);

        // After draining, it's rebuilt
        drainAllEvents();
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200)
                .body("name", equalTo("Side Effect Test"));
    }

    @Test
    @DisplayName("POST /admin/replay?fromEventId=... - Replay with filter parameter")
    void replayAll_withFromEventId() {
        given()
                .queryParam("fromEventId", UUID.randomUUID().toString())
                .when()
                .post(ADMIN_PATH + "/replay")
                .then()
                .statusCode(200)
                .body("eventsReplayed", greaterThanOrEqualTo(0));

        // IMPORTANT: drain ALL events to rebuild aggregates for other test classes
        drainAllEvents();
    }
}
