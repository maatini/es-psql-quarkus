package space.maatini.eventsourcing.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;

import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Integration tests for VertreterAggregateResource REST endpoints.
 * Tests cover aggregate queries and PostgreSQL trigger behavior.
 * 
 * Each test creates its own test data to be independent.
 */
@QuarkusTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VertreterAggregateResourceTest {

    private static final String AGGREGATES_PATH = "/aggregates/vertreter";
    private static final String EVENTS_PATH = "/events";

    private void awaitProjection() {
        // Trigger projection via REST API, draining ALL unprocessed events.
        // This is necessary because the shared DB may have many events pending
        // (e.g. after a replay test), and processBatch only handles 50 at a time.
        for (int i = 0; i < 1000; i++) {
            int processed = given()
                    .post("/admin/projection/trigger")
                    .then()
                    .statusCode(200)
                    .extract().path("processed");
            if (processed == 0)
                break;
        }
    }

    // ==================== HAPPY PATH TESTS ====================

    @Test
    @Order(1)
    @DisplayName("GET /aggregates/vertreter/{id} - Existing aggregate returns 200")
    void getExistingAggregate_returns200() {
        String testId = "get-test-" + UUID.randomUUID().toString().substring(0, 8);
        String testEmail = testId + "@example.com";

        // Create test data first
        createVertreterEvent(testId, "Test Name", testEmail);

        given()
                .when()
                .get(AGGREGATES_PATH + "/" + testId)
                .then()
                .statusCode(200)
                .body("id", equalTo(testId))
                .body("name", equalTo("Test Name"))
                .body("email", equalTo(testEmail))
                .body("version", equalTo(1));
    }

    @Test
    @Order(2)
    @DisplayName("GET /aggregates/vertreter - List all returns 200")
    void listAll_returns200() {
        given()
                .when()
                .get(AGGREGATES_PATH)
                .then()
                .statusCode(200)
                .body("size()", greaterThanOrEqualTo(0));
    }

    @Test
    @DisplayName("GET /aggregates/vertreter/{id} - Non-existing returns 404")
    void getNonExistingAggregate_returns404() {
        given()
                .when()
                .get(AGGREGATES_PATH + "/non-existing-id-12345")
                .then()
                .statusCode(404)
                .body("error", equalTo("Vertreter not found"));
    }

    @Test
    @Order(3)
    @DisplayName("GET /aggregates/vertreter/email/{email} - Existing email returns 200")
    void getByExistingEmail_returns200() {
        String testId = "email-test-" + UUID.randomUUID().toString().substring(0, 8);
        String testEmail = testId + "@test-email.com";

        createVertreterEvent(testId, "Email Test", testEmail);

        given()
                .when()
                .get(AGGREGATES_PATH + "/email/" + testEmail)
                .then()
                .statusCode(200)
                .body("email", equalTo(testEmail));
    }

    @Test
    @DisplayName("GET /aggregates/vertreter/email/{email} - Non-existing email returns 404")
    void getByNonExistingEmail_returns404() {
        given()
                .when()
                .get(AGGREGATES_PATH + "/email/nonexistent@nowhere.com")
                .then()
                .statusCode(404)
                .body("error", equalTo("Vertreter not found"));
    }

    @Test
    @Order(4)
    @DisplayName("GET /aggregates/vertreter/vertretene-person/{id} - Returns list")
    void getByVertretenePersonId_returnsList() {
        String testId = "vp-test-" + UUID.randomUUID().toString().substring(0, 8);
        String personId = "person-" + UUID.randomUUID().toString().substring(0, 8);

        // Create event with vertretene Person
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "VP Representative",
                                "email": "vp@example.com",
                                "vertretenePerson": {
                                    "id": "%s",
                                    "name": "Represented Person"
                                }
                            }
                        }
                        """.formatted(UUID.randomUUID(), testId, testId, personId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Query by represented person
        given()
                .when()
                .get(AGGREGATES_PATH + "/vertretene-person/" + personId)
                .then()
                .statusCode(200)
                .body("size()", equalTo(1))
                .body("[0].id", equalTo(testId))
                .body("[0].vertretenePerson.id", equalTo(personId));
    }

    @Test
    @Order(4)
    @DisplayName("GET /aggregates/vertreter/count - Returns count")
    void count_returnsCount() {
        given()
                .when()
                .get(AGGREGATES_PATH + "/count")
                .then()
                .statusCode(200)
                .body("count", greaterThanOrEqualTo(0));
    }

    // ==================== TRIGGER BEHAVIOR TESTS ====================

    @Test
    @Order(10)
    @DisplayName("Event creates new aggregate with vertretene Person")
    void eventCreatesNewAggregateWithVertretenePerson() {
        String newId = "vp-trigger-" + UUID.randomUUID().toString().substring(0, 8);
        String newEmail = newId + "@test.com";

        // Create event with vertretene Person
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "Vertreter Name",
                                "email": "%s",
                                "vertretenePerson": {
                                    "id": "p001",
                                    "name": "Erika Musterfrau"
                                }
                            }
                        }
                        """.formatted(UUID.randomUUID(), newId, newId, newEmail))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Verify aggregate was created with vertretene Person
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + newId)
                .then()
                .statusCode(200)
                .body("id", equalTo(newId))
                .body("name", equalTo("Vertreter Name"))
                .body("email", equalTo(newEmail))
                .body("vertretenePerson.id", equalTo("p001"))
                .body("vertretenePerson.name", equalTo("Erika Musterfrau"))
                .body("version", equalTo(1));
    }

    @Test
    @Order(11)
    @DisplayName("Second event updates aggregate and increments version")
    void secondEventUpdatesAggregate() {
        String updateId = "update-test-" + UUID.randomUUID().toString().substring(0, 8);

        // Create first event
        createVertreterEvent(updateId, "Original Name", updateId + "@v1.com");

        // Verify initial state
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + updateId)
                .then()
                .statusCode(200)
                .body("version", equalTo(1));

        // Create second event with updated data
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.updated",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "Updated Name",
                                "email": "%s"
                            }
                        }
                        """.formatted(UUID.randomUUID(), updateId, updateId, updateId + "@v2.com"))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Verify aggregate was updated
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + updateId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Name"))
                .body("email", equalTo(updateId + "@v2.com"))
                .body("version", equalTo(2));
    }

    @Test
    @Order(12)
    @DisplayName("Delete event removes aggregate")
    void deleteEventRemovesAggregate() {
        String deleteId = "delete-test-" + UUID.randomUUID().toString().substring(0, 8);

        // Create aggregate first
        createVertreterEvent(deleteId, "To Delete", deleteId + "@delete.com");

        // Verify it exists
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + deleteId)
                .then()
                .statusCode(200);

        // Send delete event
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.deleted",
                            "subject": "%s",
                            "data": {
                                "id": "%s"
                            }
                        }
                        """.formatted(UUID.randomUUID(), deleteId, deleteId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Verify aggregate was deleted
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + deleteId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(13)
    @DisplayName("Non-Vertreter event type does not create aggregate")
    void nonVertreterEventDoesNotCreateAggregate() {
        String otherId = "other-event-" + UUID.randomUUID().toString().substring(0, 8);

        // Send event with different type
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/other-service",
                            "type": "de.other.event",
                            "data": {
                                "id": "%s",
                                "name": "Should Not Appear"
                            }
                        }
                        """.formatted(UUID.randomUUID(), otherId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Should not exist as aggregate
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + otherId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(14)
    @DisplayName("Partial update preserves existing fields")
    void partialUpdatePreservesFields() {
        String partialId = "partial-" + UUID.randomUUID().toString().substring(0, 8);
        String originalEmail = partialId + "@original.com";

        // Create with full data
        createVertreterEvent(partialId, "Full Name", originalEmail);

        // Send update with only name (no email in data)
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.updated",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "Updated Only Name"
                            }
                        }
                        """.formatted(UUID.randomUUID(), partialId, partialId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Verify name updated but email preserved (due to COALESCE in trigger)
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + partialId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Updated Only Name"))
                .body("email", equalTo(originalEmail)); // Should be preserved
    }

    @Test
    @Order(15)
    @DisplayName("NULL fields in data are handled correctly")
    void nullFieldsHandled() {
        String nullTestId = "null-test-" + UUID.randomUUID().toString().substring(0, 8);

        // Create with explicit null email
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "Has Name",
                                "email": null
                            }
                        }
                        """.formatted(UUID.randomUUID(), nullTestId, nullTestId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Verify aggregate exists with null email
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + nullTestId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Has Name"))
                .body("email", is(nullValue()));
    }

    // ==================== HANDLER EDGE CASE TESTS ====================

    @Test
    @Order(20)
    @DisplayName("Event with missing data.id is processed without error")
    void eventWithMissingIdInData_stillProcessed() {
        // Send a vertreter.created event without data.id
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.created",
                            "data": {"name": "No ID Protagonist", "email": "noid@test.com"}
                        }
                        """.formatted(UUID.randomUUID()))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        // Trigger projection – should not crash
        awaitProjection();
    }

    @Test
    @Order(21)
    @DisplayName("Delete event for non-existing aggregate does not crash")
    void deleteNonExistingAggregate_noError() {
        String nonExistingId = "ghost-" + UUID.randomUUID().toString().substring(0, 8);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.deleted",
                            "data": {"id": "%s"}
                        }
                        """.formatted(UUID.randomUUID(), nonExistingId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        // Should not crash
        awaitProjection();

        // Still 404
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + nonExistingId)
                .then()
                .statusCode(404);
    }

    @Test
    @Order(22)
    @DisplayName("Update event without prior create acts as upsert")
    void updateNonExistingAggregate_createsNew() {
        String upsertId = "upsert-" + UUID.randomUUID().toString().substring(0, 8);

        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.updated",
                            "data": {"id": "%s", "name": "Born from Update", "email": "upsert@test.com"}
                        }
                        """.formatted(UUID.randomUUID(), upsertId))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();

        // Should exist even though we never sent a .created event
        given()
                .when()
                .get(AGGREGATES_PATH + "/" + upsertId)
                .then()
                .statusCode(200)
                .body("name", equalTo("Born from Update"));
    }

    @Test
    @Order(23)
    @DisplayName("Full lifecycle: create → update → update → delete")
    void multipleHandlersInSequence() {
        String lcId = "lifecycle-" + UUID.randomUUID().toString().substring(0, 8);

        // Create
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.created",
                 "data": {"id": "%s", "name": "V1", "email": "lc@test.com"}}
                """.formatted(UUID.randomUUID(), lcId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + lcId).then().statusCode(200)
                .body("name", equalTo("V1")).body("version", equalTo(1));

        // Update 1
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.updated",
                 "data": {"id": "%s", "name": "V2"}}
                """.formatted(UUID.randomUUID(), lcId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + lcId).then().statusCode(200)
                .body("name", equalTo("V2")).body("version", equalTo(2));

        // Update 2
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.updated",
                 "data": {"id": "%s", "email": "lc-v3@test.com"}}
                """.formatted(UUID.randomUUID(), lcId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + lcId).then().statusCode(200)
                .body("name", equalTo("V2")) // preserved
                .body("email", equalTo("lc-v3@test.com"))
                .body("version", equalTo(3));

        // Delete
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.deleted",
                 "data": {"id": "%s"}}
                """.formatted(UUID.randomUUID(), lcId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + lcId).then().statusCode(404);
    }

    @Test
    @Order(24)
    @DisplayName("Update vertretene Person data on existing aggregate")
    void updateVertretenePersonData() {
        String vpId = "vp-update-" + UUID.randomUUID().toString().substring(0, 8);

        // Create without vertretene Person
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.created",
                 "data": {"id": "%s", "name": "VP Holder", "email": "vp-h@test.com"}}
                """.formatted(UUID.randomUUID(), vpId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + vpId).then().statusCode(200)
                .body("vertretenePerson", is(nullValue()));

        // Update with vertretene Person
        given().contentType(ContentType.JSON).body("""
                {"id": "%s", "source": "/t", "type": "space.maatini.vertreter.updated",
                 "data": {"id": "%s", "vertretenePerson": {"id": "p-new-1", "name": "New Person"}}}
                """.formatted(UUID.randomUUID(), vpId))
                .post(EVENTS_PATH).then().statusCode(201);
        awaitProjection();

        given().get(AGGREGATES_PATH + "/" + vpId).then().statusCode(200)
                .body("vertretenePerson.id", equalTo("p-new-1"))
                .body("vertretenePerson.name", equalTo("New Person"));
    }

    // ==================== HELPER METHODS ====================

    private void createVertreterEvent(String vertreterId, String name, String email) {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                            "id": "%s",
                            "source": "/test-service",
                            "type": "space.maatini.vertreter.updated",
                            "subject": "%s",
                            "data": {
                                "id": "%s",
                                "name": "%s",
                                "email": "%s"
                            }
                        }
                        """.formatted(UUID.randomUUID(), vertreterId, vertreterId, name, email))
                .when()
                .post(EVENTS_PATH)
                .then()
                .statusCode(201);

        awaitProjection();
    }
}
