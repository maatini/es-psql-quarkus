package space.maatini.eventsourcing.resource;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;


import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.*;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;

/**
 * Integration tests for EventResource REST endpoints.
 * Tests cover happy path, validation edge cases, and boundary conditions.
 */
@QuarkusTest
@io.quarkus.test.junit.TestProfile(space.maatini.eventsourcing.TestProfile.class)

class EventResourceTest {

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        io.restassured.RestAssured.given()
            .post("/test-support/wipe")
            .then()
            .statusCode(200);
    }

    private static final String EVENTS_PATH = "/events";

    // ==================== HAPPY PATH TESTS ====================

    @Nested
    @DisplayName("Happy Path")

    class HappyPath {

        @Test
        @DisplayName("POST /events - Create valid event returns 201")
        void createValidEvent_returns201() {
            String eventId = UUID.randomUUID().toString();

            given()
                    .contentType(ContentType.JSON)
                    .body(createValidEvent(eventId, "test-001"))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("id", equalTo(eventId))
                    .body("type", equalTo("space.maatini.vertreter.updated"))
                    .body("source", equalTo("/test-service"))
                    .body("data.id", equalTo("test-001"))
                    .body("specversion", equalTo("1.0"));
        }

        @Test
        @DisplayName("POST /events - Duplicate event ID returns 200 (idempotent)")
        void duplicateEventId_returns200() {
            String eventId = UUID.randomUUID().toString();
            String eventJson = createValidEvent(eventId, "test-idempotent");

            // First request - should create
            given()
                    .contentType(ContentType.JSON)
                    .body(eventJson)
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);

            // Second request with same ID - should return existing (idempotent)
            given()
                    .contentType(ContentType.JSON)
                    .body(eventJson)
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(eventId));
        }

        @Test
        @DisplayName("GET /events/{id} - Get existing event returns 200")
        void getExistingEvent_returns200() {
            String eventId = UUID.randomUUID().toString();

            // Create event first
            given()
                    .contentType(ContentType.JSON)
                    .body(createValidEvent(eventId, "test-get"))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);

            // Get the event
            given()
                    .when()
                    .get(EVENTS_PATH + "/" + eventId)
                    .then()
                    .statusCode(200)
                    .body("id", equalTo(eventId));
        }

        @Test
        @DisplayName("GET /events/{id} - Non-existing event returns 404")
        void getNonExistingEvent_returns404() {
            given()
                    .when()
                    .get(EVENTS_PATH + "/" + UUID.randomUUID())
                    .then()
                    .statusCode(404);
        }

        @Test
        @DisplayName("GET /events/subject/{subject} - Returns events for subject")
        void getEventsBySubject_returnsEvents() {
            String subject = "subject-" + UUID.randomUUID();
            String eventId = UUID.randomUUID().toString();

            // Create event with specific subject
            given()
                    .contentType(ContentType.JSON)
                    .body(createValidEventWithSubject(eventId, "test-subject", subject))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);

            // Query by subject
            given()
                    .when()
                    .get(EVENTS_PATH + "/subject/" + subject)
                    .then()
                    .statusCode(200)
                    .body("size()", greaterThanOrEqualTo(1));
        }

        @Test
        @DisplayName("GET /events/type/{type} - Returns events by type")
        void getEventsByType_returnsEvents() {
            given()
                    .when()
                    .get(EVENTS_PATH + "/type/space.maatini.vertreter.updated")
                    .then()
                    .statusCode(200)
                    .body("size()", greaterThanOrEqualTo(0));
        }

        @Test
        @DisplayName("POST /events - Concurrency conflict returns 409")
        void concurrencyConflict_returns409() {
            String subject = "conflict-" + UUID.randomUUID();
            String event1Id = UUID.randomUUID().toString();
            String event2Id = UUID.randomUUID().toString();

            // First event for version 1 - SUCCESS
            given()
                    .contentType(ContentType.JSON)
                    .body(createValidEventWithSubject(event1Id, "data-1", subject))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);

            // Second event for SAME subject AND SAME version (1) - CONFLICT
            given()
                    .contentType(ContentType.JSON)
                    .body(createValidEventWithSubject(event2Id, "data-2", subject))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(409)
                    .body("error", equalTo("Concurrency Conflict"))
                    .body("message", containsString("Concurrency conflict"));
        }
    }

    // ==================== VALIDATION TESTS ====================

    @Nested
    @DisplayName("Validation Edge Cases")

    class ValidationEdgeCases {

        @Test
        @DisplayName("POST /events - Missing event ID returns 400")
        void missingEventId_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "source": "/test-service",
                                "type": "space.maatini.vertreter.updated",
                                "data": {"id": "v1", "name": "Test"}
                            }
                            """)
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("Validation failed"));
        }

        @Test
        @DisplayName("POST /events - Empty source returns 400")
        void emptySource_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "",
                                "type": "space.maatini.vertreter.updated",
                                "data": {"id": "v1", "name": "Test"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Invalid payload against dataschema returns 400")
        void invalidPayloadAgainstSchema_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "space.maatini.vertreter.created",
                                "dataschema": "space.maatini.vertreter.created.json",
                                "data": {
                                    "vertreterNr": "V",
                                    "status": "UNGÜLTIG"
                                }
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400)
                    .body("error", equalTo("JSON Schema Validation failed"));
        }

        @Test
        @DisplayName("POST /events - Missing type returns 400")
        void missingType_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "data": {"id": "v1", "name": "Test"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Null data returns 400")
        void nullData_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "space.maatini.vertreter.updated",
                                "data": null
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Empty data object is valid")
        void emptyDataObject_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.other.event",
                                "aggregateVersion": 1,
                                "data": {}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);
        }

        @Test
        @DisplayName("POST /events - Invalid UUID format returns 400")
        void invalidUuidFormat_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "not-a-valid-uuid",
                                "source": "/test-service",
                                "type": "space.maatini.vertreter.updated",
                                "data": {"id": "v1", "name": "Test"}
                            }
                            """)
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Malformed JSON returns 400")
        void malformedJson_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{invalid json}")
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Empty JSON object returns 400")
        void emptyJsonObject_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("{}")
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Whitespace-only source returns 400")
        void whitespaceOnlySource_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "   ",
                                "type": "de.test.event",
                                "data": {"key": "value"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }

        @Test
        @DisplayName("POST /events - Whitespace-only type returns 400")
        void whitespaceOnlyType_returns400() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "   ",
                                "data": {"key": "value"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(400);
        }
    }

    // ==================== BOUNDARY TESTS ====================

    @Nested
    @DisplayName("Boundary Cases")

    class BoundaryCases {

        @Test
        @DisplayName("POST /events - Null subject is valid")
        void nullSubject_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.event",
                                "subject": null,
                                "aggregateVersion": 1,
                                "data": {"test": "value"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("subject", is(nullValue()));
        }

        @Test
        @DisplayName("POST /events - Special characters in subject")
        void specialCharsInSubject_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.event",
                                "subject": "v/001-test_🚀",
                                "aggregateVersion": 1,
                                "data": {"test": "value"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("subject", equalTo("v/001-test_🚀"));
        }

        @Test
        @DisplayName("POST /events - Nested data object")
        void nestedDataObject_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.event",
                                "aggregateVersion": 1,
                                "data": {
                                    "id": "v1",
                                    "nested": {
                                        "level2": {
                                            "level3": "deepValue"
                                        }
                                    },
                                    "array": [1, 2, 3]
                                }
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("data.nested.level2.level3", equalTo("deepValue"))
                    .body("data.array.size()", equalTo(3));
        }

        @Test
        @DisplayName("POST /events - Custom datacontenttype")
        void customDataContentType_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.event",
                                "datacontenttype": "application/xml",
                                "aggregateVersion": 1,
                                "data": {"xml": "<test>value</test>"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("datacontenttype", equalTo("application/xml"));
        }

        @Test
        @DisplayName("POST /events - With dataschema")
        void withDataSchema_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "space.maatini.vertreter.created",
                                "dataschema": "space.maatini.vertreter.created.json",
                                "aggregateVersion": 1,
                                "data": {
                                    "vertreterNr": "V123",
                                    "name": "Max Mustermann",
                                    "status": "AKTIV"
                                }
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("dataschema", equalTo("space.maatini.vertreter.created.json"));
        }

        @Test
        @DisplayName("POST /events - With explicit time")
        void withExplicitTime_returns201() {
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.event",
                                "time": "2025-01-01T12:00:00Z",
                                "aggregateVersion": 1,
                                "data": {"id": "v1"}
                            }
                            """.formatted(UUID.randomUUID()))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201)
                    .body("time", containsString("2025-01-01"));
        }

        @Test
        @DisplayName("GET /events/subject/{subject} - Unknown subject returns empty list")
        void getEventsBySubject_unknownSubject_returnsEmptyList() {
            given()
                    .when()
                    .get(EVENTS_PATH + "/subject/totally-unknown-subject-" + UUID.randomUUID())
                    .then()
                    .statusCode(200)
                    .body("size()", equalTo(0));
        }

        @Test
        @DisplayName("GET /events/type/{type} - Unknown type returns empty list")
        void getEventsByType_unknownType_returnsEmptyList() {
            given()
                    .when()
                    .get(EVENTS_PATH + "/type/com.nonexistent.event.type." + UUID.randomUUID())
                    .then()
                    .statusCode(200)
                    .body("size()", equalTo(0));
        }

        @Test
        @DisplayName("POST /events - Very large data payload is accepted")
        void veryLargeDataPayload_accepted() {
            // Build a ~100KB value
            String largeValue = "x".repeat(100_000);
            given()
                    .contentType(ContentType.JSON)
                    .body("""
                            {
                                "id": "%s",
                                "source": "/test-service",
                                "type": "de.test.large",
                                "aggregateVersion": 1,
                                "data": {"bigField": "%s"}
                            }
                            """.formatted(UUID.randomUUID(), largeValue))
                    .when()
                    .post(EVENTS_PATH)
                    .then()
                    .statusCode(201);
        }
    }

    // ==================== HELPER METHODS ====================

    private String createValidEvent(String eventId, String dataId) {
        return """
                {
                    "id": "%s",
                    "source": "/test-service",
                    "type": "space.maatini.vertreter.updated",
                    "subject": "%s",
                    "aggregateVersion": 1,
                    "data": {
                        "id": "%s",
                        "name": "Test Vertreter",
                        "email": "test@example.com"
                    }
                }
                """.formatted(eventId, dataId, dataId);
    }

    private String createValidEventWithSubject(String eventId, String dataId, String subject) {
        return """
                {
                    "id": "%s",
                    "source": "/test-service",
                    "type": "space.maatini.vertreter.updated",
                    "subject": "%s",
                    "aggregateVersion": 1,
                    "data": {
                        "id": "%s",
                        "name": "Test Vertreter",
                        "email": "test@example.com"
                    }
                }
                """.formatted(eventId, subject, dataId);
    }
}
