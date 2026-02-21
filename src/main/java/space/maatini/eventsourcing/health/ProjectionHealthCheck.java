package space.maatini.eventsourcing.health;

import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.Readiness;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.service.ProjectionService;

@ApplicationScoped
@Readiness
public class ProjectionHealthCheck implements HealthCheck {

    private final ProjectionService projectionService;

    @Inject
    public ProjectionHealthCheck(ProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @Override
    public HealthCheckResponse call() {
        double lag = projectionService.getLagSeconds();
        HealthCheckResponseBuilder builder = HealthCheckResponse.named("projection");

        if (lag > 300) {
            builder.down().withData("lagSeconds", (long) lag).withData("status", "CRITICAL");
        } else if (lag > 30) {
            builder.up().withData("lagSeconds", (long) lag).withData("status", "WARNING");
        } else {
            builder.up().withData("lagSeconds", (long) lag).withData("status", "OK");
        }
        return builder.build();
    }
}
