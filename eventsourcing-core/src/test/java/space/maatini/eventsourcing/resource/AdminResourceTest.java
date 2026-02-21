package space.maatini.eventsourcing.resource;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

/**
 * Core AdminResource tests — no Vertreter-specific dependencies.
 * Tests that require the Vertreter example are in example-vertreter module.
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(space.maatini.eventsourcing.TestProfile.class)
@io.quarkus.test.security.TestSecurity(user = "test", roles = {"admin", "user"})
class AdminResourceTest {

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        given().post("/test-support/wipe").then().statusCode(200);
    }

    private static final String ADMIN_PATH = "/admin";

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
    @DisplayName("POST /admin/projection/trigger - Returns 0 when no unprocessed events")
    void triggerProjection_noEvents_returnsZero() {
        drainAllEvents();
        given()
                .when()
                .post(ADMIN_PATH + "/projection/trigger")
                .then()
                .statusCode(200)
                .body("processed", equalTo(0));
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
                .body("eventsReplayed", equalTo(0));

        drainAllEvents();
    }
}
