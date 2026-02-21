package space.maatini.eventsourcing.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CloudEventDTO validation.
 */
class CloudEventDTOTest {

    private static Validator validator;

    @BeforeAll
    static void setupValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Nested
    @DisplayName("Valid CloudEvents")
    class ValidCloudEvents {

        @Test
        @DisplayName("Minimal valid event")
        void minimalValidEvent() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    null, // specversion - optional
                    "de.test.event",
                    null, // subject - optional
                    null, // time - optional
                    null, // datacontenttype - optional
                    null, // dataschema - optional
                    1, // dataVersion
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty(), "Should have no violations: " + violations);
        }

        @Test
        @DisplayName("Full valid event")
        void fullValidEvent() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/my-service",
                    "1.0",
                    "space.maatini.vertreter.updated",
                    "v001",
                    OffsetDateTime.now(),
                    "application/json",
                    "https://schema.example.com/vertreter.json",
                    1, // dataVersion
                    Map.of("id", "v001", "name", "Test"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }

        @Test
        @DisplayName("Empty data map is valid")
        void emptyDataMapIsValid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    "1.0",
                    "de.test.event",
                    null,
                    null,
                    null,
                    null,
                    1, // dataVersion
                    Map.of() // empty but not null
            );

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertTrue(violations.isEmpty());
        }
    }

    @Nested
    @DisplayName("Invalid CloudEvents - Required Fields")
    class InvalidRequiredFields {

        @Test
        @DisplayName("Null ID is invalid")
        void nullIdIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    null, // null ID
                    "/source",
                    "1.0",
                    "de.test.event",
                    null,
                    null,
                    null,
                    null,
                    1,
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertEquals(1, violations.size());
            assertEquals("Event ID is required", violations.iterator().next().getMessage());
        }

        @Test
        @DisplayName("Blank source is invalid")
        void blankSourceIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "", // blank source
                    "1.0",
                    "de.test.event",
                    null,
                    null,
                    null,
                    null,
                    1,
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertEquals(1, violations.size());
            assertEquals("Source is required", violations.iterator().next().getMessage());
        }

        @Test
        @DisplayName("Null source is invalid")
        void nullSourceIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    null, // null source
                    "1.0",
                    "de.test.event",
                    null,
                    null,
                    null,
                    null,
                    1,
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Blank type is invalid")
        void blankTypeIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    "1.0",
                    "", // blank type
                    null,
                    null,
                    null,
                    null,
                    1,
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertEquals(1, violations.size());
            assertEquals("Event type is required", violations.iterator().next().getMessage());
        }

        @Test
        @DisplayName("Null type is invalid")
        void nullTypeIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    "1.0",
                    null, // null type
                    null,
                    null,
                    null,
                    null,
                    1,
                    Map.of("key", "value"));

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertFalse(violations.isEmpty());
        }

        @Test
        @DisplayName("Null data is invalid")
        void nullDataIsInvalid() {
            CloudEventDTO dto = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    "1.0",
                    "de.test.event",
                    null,
                    null,
                    null,
                    null,
                    1,
                    null // null data
            );

            Set<ConstraintViolation<CloudEventDTO>> violations = validator.validate(dto);
            assertEquals(1, violations.size());
            assertEquals("Event data is required", violations.iterator().next().getMessage());
        }
    }

    @Nested
    @DisplayName("withDefaults() method")
    class WithDefaults {

        @Test
        @DisplayName("Applies defaults to optional fields")
        void appliesDefaults() {
            CloudEventDTO original = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    null, // no specversion
                    "de.test.event",
                    null, // no subject
                    null, // no time
                    null, // no datacontenttype
                    null, // no dataschema
                    1, // dataVersion
                    Map.of("key", "value"));

            CloudEventDTO withDefaults = original.withDefaults();

            assertEquals("1.0", withDefaults.specversion());
            assertEquals("application/json", withDefaults.datacontenttype());
            assertNotNull(withDefaults.time());
            assertNull(withDefaults.subject()); // Still null - no default
            assertNull(withDefaults.dataschema()); // Still null - no default
        }

        @Test
        @DisplayName("Preserves existing values")
        void preservesExistingValues() {
            OffsetDateTime customTime = OffsetDateTime.parse("2025-06-01T12:00:00Z");
            CloudEventDTO original = new CloudEventDTO(
                    UUID.randomUUID(),
                    "/source",
                    "2.0", // custom specversion
                    "de.test.event",
                    "my-subject",
                    customTime,
                    "text/plain",
                    "https://schema.example.com",
                    1,
                    Map.of("key", "value"));

            CloudEventDTO withDefaults = original.withDefaults();

            assertEquals("2.0", withDefaults.specversion());
            assertEquals("my-subject", withDefaults.subject());
            assertEquals(customTime, withDefaults.time());
            assertEquals("text/plain", withDefaults.datacontenttype());
            assertEquals("https://schema.example.com", withDefaults.dataschema());
        }
    }
}
