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
        entity.setId("v001");
        entity.setName("Test Name");
        entity.setEmail("test@example.com");
        entity.setVertretenePersonId("p001");
        entity.setVertretenePersonName("Erika Musterfrau");
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setEventId(UUID.randomUUID());
        entity.setVersion(5);

        // When
        VertreterDTO dto = VertreterDTO.from(entity);

        // Then
        assertEquals("v001", dto.id());
        assertEquals("Test Name", dto.name());
        assertEquals("test@example.com", dto.email());
        assertNotNull(dto.vertretenePerson());
        assertEquals("p001", dto.vertretenePerson().id());
        assertEquals("Erika Musterfrau", dto.vertretenePerson().name());
        assertEquals(entity.getUpdatedAt(), dto.updatedAt());
        assertEquals(entity.getEventId(), dto.lastEventId());
        assertEquals(5, dto.version());
    }

    @Test
    @DisplayName("from() handles null vertretene Person")
    void fromHandlesNullVertetenePerson() {
        // Given
        VertreterAggregate entity = new VertreterAggregate();
        entity.setId("v002");
        entity.setName("Name Only");
        entity.setEmail("name@example.com");
        entity.setVertretenePersonId(null); // No represented person
        entity.setVertretenePersonName(null);
        entity.setUpdatedAt(OffsetDateTime.now());
        entity.setEventId(UUID.randomUUID());
        entity.setVersion(1);

        // When
        VertreterDTO dto = VertreterDTO.from(entity);

        // Then
        assertEquals("v002", dto.id());
        assertNull(dto.vertretenePerson()); // Should be null
    }

    @Test
    @DisplayName("from() handles null fields")
    void fromHandlesNullFields() {
        // Given
        VertreterAggregate entity = new VertreterAggregate();
        entity.setId("v003");
        entity.setName(null);
        entity.setEmail(null);
        entity.setVertretenePersonId(null);
        entity.setVertretenePersonName(null);
        entity.setUpdatedAt(null);
        entity.setEventId(null);
        entity.setVersion(null);

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
