package space.maatini.eventsourcing.dto;

import space.maatini.eventsourcing.entity.VertreterAggregate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for VertreterDTO.
 */
class VertreterDTOTest {

    @Test
    @DisplayName("from() maps all fields correctly")
    void fromMapsAllFields() {
        // Given
        VertreterAggregate entity = new VertreterAggregate();
        entity.id = "v001";
        entity.name = "Test Name";
        entity.email = "test@example.com";
        entity.vertretenePersonId = "p001";
        entity.vertretenePersonName = "Erika Musterfrau";
        entity.updatedAt = OffsetDateTime.now();
        entity.eventId = UUID.randomUUID();
        entity.version = 5;

        // When
        VertreterDTO dto = VertreterDTO.from(entity);

        // Then
        assertEquals("v001", dto.id());
        assertEquals("Test Name", dto.name());
        assertEquals("test@example.com", dto.email());
        assertNotNull(dto.vertretenePerson());
        assertEquals("p001", dto.vertretenePerson().id());
        assertEquals("Erika Musterfrau", dto.vertretenePerson().name());
        assertEquals(entity.updatedAt, dto.updatedAt());
        assertEquals(entity.eventId, dto.lastEventId());
        assertEquals(5, dto.version());
    }

    @Test
    @DisplayName("from() handles null vertretene Person")
    void fromHandlesNullVertetenePerson() {
        // Given
        VertreterAggregate entity = new VertreterAggregate();
        entity.id = "v002";
        entity.name = "Name Only";
        entity.email = "name@example.com";
        entity.vertretenePersonId = null;  // No represented person
        entity.vertretenePersonName = null;
        entity.updatedAt = OffsetDateTime.now();
        entity.eventId = UUID.randomUUID();
        entity.version = 1;

        // When
        VertreterDTO dto = VertreterDTO.from(entity);

        // Then
        assertEquals("v002", dto.id());
        assertNull(dto.vertretenePerson());  // Should be null
    }

    @Test
    @DisplayName("from() handles null fields")
    void fromHandlesNullFields() {
        // Given
        VertreterAggregate entity = new VertreterAggregate();
        entity.id = "v003";
        entity.name = null;
        entity.email = null;
        entity.vertretenePersonId = null;
        entity.vertretenePersonName = null;
        entity.updatedAt = null;
        entity.eventId = null;
        entity.version = null;

        // When
        VertreterDTO dto = VertreterDTO.from(entity);

        // Then
        assertEquals("v003", dto.id());
        assertNull(dto.name());
        assertNull(dto.email());
        assertNull(dto.vertretenePerson());
        assertNull(dto.updatedAt());
        assertNull(dto.lastEventId());
        assertNull(dto.version());
    }
}
