package space.maatini.eventsourcing.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.io.InputStream;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class EventValidationService {

    private static final Logger log = Logger.getLogger(EventValidationService.class);

    private final ObjectMapper objectMapper;
    private final JsonSchemaFactory factory;
    private final Map<String, JsonSchema> schemaCache = new ConcurrentHashMap<>();

    @Inject
    public EventValidationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        // Configured for Draft 7 which is widely used
        this.factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
    }

    /**
     * Validates the given data payload against the schema designated by the dataSchema identifier.
     * The schema is expected to be present in `src/main/resources/schemas/`.
     * 
     * @param dataSchema the URI or filename of the schema (e.g. "my-event-schema.json")
     * @param data the map representing the JSON data payload
     * @return a Set of validation error messages, or an empty set if valid
     */
    public Set<ValidationMessage> validate(String dataSchema, Map<String, Object> data) {
        if (dataSchema == null || dataSchema.isBlank()) {
            throw new IllegalArgumentException("No dataSchema provided for event");
        }

        JsonSchema schema = schemaCache.computeIfAbsent(dataSchema, this::loadSchema);
        if (schema == null) {
            throw new IllegalArgumentException("Schema not found in registry: " + dataSchema);
        }

        JsonNode dataNode = objectMapper.valueToTree(data);
        return schema.validate(dataNode);
    }

    private JsonSchema loadSchema(String schemaName) {
        String resourcePath = "schemas/" + extractFilename(schemaName);
        InputStream is = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath);
        if (is == null) {
            log.warnf("Could not find JSON schema at classpath: %s", resourcePath);
            return null;
        }
        try {
            return factory.getSchema(is);
        } catch (Exception e) {
            log.errorf(e, "Failed to parse JSON Schema from %s", resourcePath);
            return null;
        }
    }

    private String extractFilename(String dataSchemaUri) {
        // If it's a full URI like "http://example.com/schemas/my-event.json", extract the last part
        if (dataSchemaUri.contains("/")) {
            return dataSchemaUri.substring(dataSchemaUri.lastIndexOf('/') + 1);
        }
        return dataSchemaUri;
    }
}
