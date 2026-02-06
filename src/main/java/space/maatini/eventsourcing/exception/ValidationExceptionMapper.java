package space.maatini.eventsourcing.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

/**
 * Global exception mapper for validation errors.
 */
@Provider
public class ValidationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<ValidationError> errors = exception.getConstraintViolations().stream()
            .map(this::toValidationError)
            .toList();

        return Response.status(Response.Status.BAD_REQUEST)
            .entity(new ValidationErrorResponse("Validation failed", errors))
            .build();
    }

    private ValidationError toValidationError(ConstraintViolation<?> violation) {
        String field = violation.getPropertyPath().toString();
        // Remove method name prefix if present (e.g., "createEvent.event.id" -> "event.id")
        if (field.contains(".")) {
            field = field.substring(field.indexOf('.') + 1);
        }
        return new ValidationError(field, violation.getMessage());
    }

    public record ValidationError(String field, String message) {}
    public record ValidationErrorResponse(String error, List<ValidationError> violations) {}
}
