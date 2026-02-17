package space.maatini.eventsourcing.service;

import space.maatini.eventsourcing.entity.CloudEvent;
import io.smallrye.mutiny.Uni;

/**
 * Interface für typspezifische Event-Handler.
 * Jeder neue Event-Typ bekommt eine eigene @ApplicationScoped Implementierung.
 */
public interface VertreterEventHandler {

    /**
     * Prüft, ob dieser Handler den Event-Typ verarbeiten kann.
     */
    boolean canHandle(String eventType);

    /**
     * Verarbeitet den Event (Upsert/Delete etc.).
     */
    Uni<Void> handle(CloudEvent event);
}