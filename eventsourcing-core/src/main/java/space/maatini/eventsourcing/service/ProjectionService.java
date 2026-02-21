package space.maatini.eventsourcing.service;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.UUID;

/**
 * Facade for projection operations.
 * Internals have been split into EventBatchProcessor, EventNotificationListener, 
 * ProjectionMetrics and ProjectionReplayService to satisfy SRP.
 */
@ApplicationScoped
public class ProjectionService {

    private final EventBatchProcessor batchProcessor;
    private final ProjectionReplayService replayService;
    private final ProjectionMetrics metrics;

    @Inject
    public ProjectionService(EventBatchProcessor batchProcessor, ProjectionReplayService replayService, ProjectionMetrics metrics) {
        this.batchProcessor = batchProcessor;
        this.replayService = replayService;
        this.metrics = metrics;
    }

    public double getLagSeconds() {
        return metrics.getLagSeconds();
    }

    public Uni<Integer> triggerManualBatch() {
        return batchProcessor.triggerManualBatch();
    }

    public Uni<Integer> replayAll(UUID fromEventId) {
        return replayService.replayAll(fromEventId);
    }
}
