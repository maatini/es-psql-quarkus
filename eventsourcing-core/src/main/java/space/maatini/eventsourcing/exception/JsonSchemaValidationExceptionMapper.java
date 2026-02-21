package space.maatini.eventsourcing.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import java.util.stream.Collectors;

@Provider
public class JsonSchemaValidationExceptionMapper implements ExceptionMapper<JsonSchemaValidationException> {

    @Override
    public Response toResponse(JsonSchemaValidationException exception) {
        List<ValidationError> errors = exception.getValidationMessages().stream()
            .map(msg -> new ValidationError("schema-validation", msg.getMessage()))
            .collect(Collectors.toList());

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ValidationErrorResponse("JSON Schema Validation failed", errors))
            .build();
    }

    public record ValidationError(String field, String message) {}
    public record ValidationErrorResponse(String error, List<ValidationError> violations) {}
}
