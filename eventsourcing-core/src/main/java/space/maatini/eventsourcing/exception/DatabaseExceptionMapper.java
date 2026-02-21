package space.maatini.eventsourcing.exception;

import space.maatini.eventsourcing.dto.ErrorResponse;

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
            String constraintName = exception.getConstraintName().toLowerCase();
            if (constraintName.contains("subject_version") || constraintName.contains("subject_aggregate_version") || constraintName.contains("concurrency")) {
                message = "Concurrency conflict: Another version of this aggregate was already stored. Please reload and try again.";
            } else {
                message = "Constraint violation: " + exception.getConstraintName();
            }
        }

        return Response.status(Response.Status.CONFLICT)
                .entity(new ErrorResponse("Concurrency Conflict", message))
                .build();
    }
}
