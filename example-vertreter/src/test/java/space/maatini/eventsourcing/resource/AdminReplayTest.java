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
 * Admin replay tests that require the Vertreter example bounded context.
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(space.maatini.eventsourcing.TestProfile.class)
@io.quarkus.test.security.TestSecurity(user = "test", roles = {"admin", "user"})
class AdminReplayTest {

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        given().post("/test-support/wipe").then().statusCode(200);
    }

    private static final String ADMIN_PATH = "/admin";
    private static final String EVENTS_PATH = "/events";
    private static final String AGGREGATES_PATH = "/aggregates/vertreter";

    private void drainAllEvents() {
        for (int i = 0; i < 1000; i++) {
            int processed = given()
                    .post(ADMIN_PATH + "/projection/trigger")
                    .then()
                    .statusCode(200)
                    .extract().path("processed");
            if (processed == 0) break;
        }
    }

    @Test
    @DisplayName("POST /admin/projection/trigger - Returns 200 with processed count")
    void triggerProjection_returns200() {
        String vertreterId = "admin-trigger-" + UUID.randomUUID().toString().substring(0, 8);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "aggregateVersion": 1,
                            "data": {"id": "%s", "name": "Admin Trigger Test", "email": "admin-trigger@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        given()
                .when()
                .post(ADMIN_PATH + "/projection/trigger")
                .then()
                .statusCode(200)
                .body("processed", greaterThanOrEqualTo(0));

        drainAllEvents();
    }

    @Test
    @DisplayName("POST /admin/replay - Replays all events and returns count")
    void replayAll_returns200() {
        String vertreterId = "replay-test-" + UUID.randomUUID().toString().substring(0, 8);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "aggregateVersion": 1,
                            "data": {"id": "%s", "name": "Replay Test", "email": "replay@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        drainAllEvents();
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200);

        given()
                .when()
                .post(ADMIN_PATH + "/replay")
                .then()
                .statusCode(200)
                .body("eventsReplayed", greaterThanOrEqualTo(1));

        drainAllEvents();
    }

    @Test
    @DisplayName("POST /admin/replay - Side effect: aggregates are rebuilt after replay + trigger")
    void replayAll_rebuildsSideEffects() {
        String vertreterId = "replay-side-" + UUID.randomUUID().toString().substring(0, 8);
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "aggregateVersion": 1,
                            "data": {"id": "%s", "name": "Side Effect Test", "email": "side@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), vertreterId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        drainAllEvents();
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200);

        given().post(ADMIN_PATH + "/replay").then().statusCode(200);
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(404);

        drainAllEvents();
        given().get(AGGREGATES_PATH + "/" + vertreterId).then().statusCode(200)
                .body("name", equalTo("Side Effect Test"));
    }
}
