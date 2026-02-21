# es-psql-quarkus

![Build Status](https://github.com/maatini/es-psql-quarkus/actions/workflows/ci.yml/badge.svg)

**High-Performance Event Sourcing Template** mit Quarkus 3.31, CloudEvents und vollständigem **CQRS-Muster** via PostgreSQL LISTEN/NOTIFY.

## Architektur

```mermaid
graph TD
    subgraph "Write Side (Command)"
        CMD["REST API<br/>(VertreterCommandResource)<br/>POST /commands/vertreter"] --> CS["VertreterCommandService"]
        CS --> |"Replay-Invariant-Prüfung"| VA["Vertreter<br/>(Domain Aggregate)"]
        VA --> |"Neue Events"| ES_RAW["EventService<br/>POST /events"]
        ES_RAW --> |"INSERT"| DB_Events[("PostgreSQL<br/>events table")]
    end

    subgraph "Database Layer"
        DB_Events --> |"TRIGGER (After Insert)"| DB_Notify["NOTIFY events_channel"]
    end

    subgraph "Async Projection"
        DB_Notify --> |"LISTEN"| PROJ["ProjectionService<br/>+ Handler Registry"]
        PROJ --> |"Handler Pattern"| DB_Agg[("PostgreSQL<br/>vertreter_aggregate")]
    end

    subgraph "Read Side (Query)"
        API_R["REST API<br/>(VertreterAggregateResource)<br/>GET /aggregates"] --> AS["VertreterAggregateService"]
        AS --> |"SELECT"| DB_Agg
    end

    classDef java fill:#2C3E50,stroke:#fff,stroke-width:2px,color:#fff;
    classDef db fill:#27AE60,stroke:#fff,stroke-width:2px,color:#fff;

    class CMD,CS,VA,ES_RAW,API_R,AS,PROJ java;
    class DB_Events,DB_Agg,DB_Notify db;
```

**Kernprinzipien:**
- **Commands** prüfen Invarianten (Aggregate-Replay) bevor Events gespeichert werden
- **SQL-Triggers** nur noch für NOTIFY – die gesamte Aggregationslogik liegt in Java
- **Optimistic Locking** via JPA `@Version` – verhindert Race Conditions

## Voraussetzungen

- [Devbox](https://www.jetify.com/devbox/docs/installing_devbox/) installiert

## Schnellstart

```bash
# Devbox-Shell aktivieren
devbox shell

# PostgreSQL starten und Datenbank erstellen
devbox run pg:create

# Quarkus im Dev-Modus starten
devbox run quarkus:dev
```

API: http://localhost:8080  
Swagger UI: http://localhost:8080/q/swagger-ui

## API Endpoints

### Commands (Write Side – mit Invariant-Prüfung)
| Method   | Path                       | Beschreibung                              |
|----------|----------------------------|-------------------------------------------|
| `POST`   | `/commands/vertreter`      | Vertreter anlegen (Duplikat → 400)        |
| `PUT`    | `/commands/vertreter/{id}` | Vertreter aktualisieren (nicht existent → 400) |
| `DELETE` | `/commands/vertreter/{id}` | Vertreter löschen                         |

### Events (Low-Level Write)
| Method | Path                        | Beschreibung                       |
|--------|-----------------------------|------------------------------------|
| `POST` | `/events`                   | CloudEvent speichern (idempotent)  |
| `GET`  | `/events/{id}`              | Event abrufen                      |
| `GET`  | `/events/subject/{subject}` | Events nach Subject                |
| `GET`  | `/events/type/{type}`       | Events nach Typ                    |

### Vertreter Aggregates (Read Side)
| Method | Path                                           | Beschreibung                  |
|--------|------------------------------------------------|-------------------------------|
| `GET`  | `/aggregates/vertreter`                        | Alle Vertreter                |
| `GET`  | `/aggregates/vertreter/{id}`                   | Vertreter nach ID             |
| `GET`  | `/aggregates/vertreter/email/{email}`          | Vertreter nach E-Mail         |
| `GET`  | `/aggregates/vertreter/count`                  | Anzahl Vertreter              |
| `GET`  | `/aggregates/vertreter/vertretene-person/{id}` | Vertreter einer Person        |

### Admin & Ops
| Method | Path                        | Beschreibung                              |
|--------|-----------------------------|-------------------------------------------|
| `POST` | `/admin/projection/trigger` | Projection manuell triggern               |
| `POST` | `/admin/replay`             | Replay (optional `?fromEventId=UUID`)     |
| `GET`  | `/q/health`                 | Health Status (inkl. Projection-Lag)      |
| `GET`  | `/q/metrics`                | Prometheus Metriken                       |

## Features

- **True CQRS** – Command-Side mit Domänen-Aggregaten und Invariant-Prüfung
- Near-Realtime Updates durch PostgreSQL LISTEN/NOTIFY
- Vollständige Revisionssicherheit (unveränderlicher Event-Log)
- **Optimistic Locking** (JPA `@Version`) für Race Condition-Schutz
- **DB Constraints**: `UNIQUE(email)`, `CHECK (version >= 0)`
- Handler-Pattern für beliebig viele Aggregate
- Replay-Fähigkeit (kompletter Neuaufbau des Read-Models)
- **Robustes Error Handling**: Automatischer Retry & Dead-Letter-Logik
- **Monitoring**: Micrometer/Prometheus + Custom HealthChecks
- Umfassende Test-Suite (68 Tests) – voll isoliert via `@BeforeEach`-DB-Wipe
- Devbox-Komplettumgebung

## Paketstruktur

```
src/main/java/space/maatini/eventsourcing/
├── domain/                   # Domänen-Aggregate (Invarianten, Schreib-Logik)
│   ├── AggregateRoot.java    # Basisklasse für Domain-Aggregate
│   └── Vertreter.java
├── dto/
│   └── command/              # Command-DTOs
│       ├── CreateVertreterCommand.java
│       ├── UpdateVertreterCommand.java
│       └── VertretenePersonCommandDTO.java
├── entity/                   # JPA Read-Models (Projektions-Tabellen)
│   ├── AggregateRoot.java    # Marker-Interface für Entitäten
│   ├── CloudEvent.java
│   └── VertreterAggregate.java
├── resource/                 # REST-Endpunkte
│   ├── VertreterCommandResource.java
│   ├── VertreterAggregateResource.java
│   ├── EventResource.java
│   └── AdminResource.java
└── service/                  # Applikationslogik & Handler
    ├── VertreterCommandService.java
    ├── VertreterCreatedOrUpdatedHandler.java
    ├── VertreterDeletedHandler.java
    ├── ProjectionService.java          # Facade
    ├── EventBatchProcessor.java
    ├── EventHandlerRegistry.java
    ├── ProjectionReplayService.java
    └── EventNotificationListener.java  # PG LISTEN (deaktiviert im Test-Profil)
```

## Neues Aggregat hinzufügen

### Schritt 1: Flyway-Migration & Read-Model (Entity Layer)

```sql
-- src/main/resources/db/migration/V11__abwesenheit.sql
CREATE TABLE abwesenheit_aggregate (
    id TEXT PRIMARY KEY,
    grund TEXT,
    event_id UUID,
    version INTEGER,
    updated_at TIMESTAMPTZ
);
```

```java
// Entität implementiert das Marker-Interface
@Entity @Table(name = "abwesenheit_aggregate")
public class AbwesenheitAggregate extends PanacheEntityBase implements space.maatini.eventsourcing.entity.AggregateRoot {
    @Id public String id;
    public String grund;
    public UUID eventId;
    @Version public Integer version;
    public OffsetDateTime updatedAt;
}
```

### Schritt 2: Event-Handler (Projection Layer)

```java
@ApplicationScoped
@HandlesEvents(value = "space.maatini.abwesenheit.", aggregate = AbwesenheitAggregate.class)
public class AbwesenheitHandler implements AggregateEventHandler<AbwesenheitAggregate> {

    @Override
    public boolean canHandle(String eventType) {
        return eventType.startsWith("space.maatini.abwesenheit.");
    }

    @Override
    public Uni<Void> handle(CloudEvent event) {
        // ... Logik zur Aktualisierung der AbwesenheitAggregate Entität
    }
}
```

### Schritt 3: Domain-Aggregat (Command Layer)

```java
// Erbt von der Domänen-Basisklasse
public class Abwesenheit extends space.maatini.eventsourcing.domain.AggregateRoot {
    public Abwesenheit(String id) { super(id); }

    public void create(CreateAbwesenheitCommand cmd) {
        if (getVersion() > 0) throw new IllegalStateException("Bereits erstellt");
        // ... Event erzeugen und applyNewEvent() aufrufen
    }

    @Override protected void mutate(CloudEvent event) {
        // Zustandsübergänge für das Replaying der Invarianten
    }
}
```

**Das war's.** Der `EventHandlerRegistry` erkennt den Handler automatisch beim Start.

## Tests

```bash
# Unit + Integrationstests
./mvnw test

# Load-Test (k6)
devbox run k6 run benchmarks/load-test.js
```

### Performance (Linux Devbox – aktuelle Messung 21.02.2026)

| Metrik | Ergebnis |
|--------|----------|
| **Iterationen** | 14.962 (in 100 s) |
| **Throughput** | ~149 Iterationen/s |
| **HTTP-Requests gesamt** | 44.886 (∼448 req/s) |
| **P90 Latency** | 4.75 ms |
| **P95 Latency** | 5.47 ms ✅ (Threshold: < 100 ms) |
| **P95 Latency (nur 2xx)** | 5.83 ms |
| **Business Error Rate** | 0% |
| **VUs** | 20 |

> **Hinweis zur Poll-Rate (~33% HTTP-Fails):** Der Load-Test pollt `GET /aggregates` nach jedem
> `POST /events` bis die Projektion fertig ist (Eventual Consistency). Diese 404-Antworten
> sind kein Fehler — Business-Error-Rate = **0%**.

*Messung auf Linux x86-64 (Devbox), PostgreSQL lokal.*

## Devbox Befehle

| Befehl                   | Beschreibung            |
|--------------------------|-------------------------|
| `devbox run pg:start`    | PostgreSQL starten      |
| `devbox run pg:create`   | Datenbank erstellen     |
| `devbox run quarkus:dev` | App im Dev-Modus        |

## Production Readiness

- Replay-Endpoint für Recovery
- Prometheus-Metriken & Custom Health Check (inkl. Projection-Lag)
- Dead-Letter-Queue bei permanenten Fehlern
- Multi-Instance-fähig (`FOR UPDATE SKIP LOCKED`)
- Native Executable unterstützt (GraalVM)
- `UNIQUE` + optimistic Locking schützen vor Race Conditions

---

**Lizenz:** MIT  
**Copyright:** 2026 Martin Richardt
