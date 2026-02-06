package de.antigravity.eventsourcing.exception;

import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.hibernate.exception.ConstraintViolationException;
import org.jboss.logging.Logger;

/**
 * Global exception mapper for database constraint violations.
 */
@Provider
public class DatabaseExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger LOG = Logger.getLogger(DatabaseExceptionMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        LOG.error("Database constraint violation", exception);
        
        String message = "Database constraint violation";
        if (exception.getConstraintName() != null) {
            message = "Constraint violation: " + exception.getConstraintName();
        }

        return Response.status(Response.Status.CONFLICT)
            .entity(new ErrorResponse("Database error", message))
            .build();
    }

    public record ErrorResponse(String error, String message) {}
}
