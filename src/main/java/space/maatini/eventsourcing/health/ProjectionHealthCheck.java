package space.maatini.eventsourcing.health;

import io.quarkus.health.HealthCheck;
import io.quarkus.health.HealthCheckResponse;
import io.quarkus.health.HealthCheckResponseBuilder;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import space.maatini.eventsourcing.service.ProjectionService;

@ApplicationScoped
@Health
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
            builder.down().withData("lagSeconds", lag).withData("status", "CRITICAL");
        } else if (lag > 30) {
            builder.up().withData("lagSeconds", lag).withData("status", "WARNING");
        } else {
            builder.up().withData("lagSeconds", lag).withData("status", "OK");
        }
        return builder.build();
    }
}
