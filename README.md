# es-psql-quarkus

![Project Banner](doc/banner_placeholder.png)


High-Performance Event Sourcing Template mit Quarkus und PostgreSQL Listen/Notify.
Ein reaktiver Eventsourcing-Microservice mit Quarkus, CloudEvents und asynchronen PostgreSQL-Projektionen (CQRS).

## Voraussetzungen

- [Devbox](https://www.jetify.com/devbox/docs/installing_devbox/) installiert

## Schnellstart

```bash
# Devbox-Shell aktivieren
devbox shell

# PostgreSQL starten und Datenbank erstellen
devbox run pg:create

# Quarkus im Dev-Modus starten
./mvnw quarkus:dev
```

Die API ist dann unter http://localhost:8080 erreichbar.
Swagger UI: http://localhost:8080/q/swagger-ui

## API Endpoints

### Events

| Method | Path | Beschreibung |
|--------|------|--------------|
| `POST` | `/events` | CloudEvent speichern (idempotent) |
| `GET` | `/events/{id}` | Event abrufen |
| `GET` | `/events/subject/{subject}` | Events nach Subject |
| `GET` | `/events/type/{type}` | Events nach Typ |

### Vertreter Aggregates

| Method | Path | Beschreibung |
|--------|------|--------------|
| `GET` | `/aggregates/vertreter` | Alle Vertreter |
| `GET` | `/aggregates/vertreter/{id}` | Vertreter nach ID |
| `GET` | `/aggregates/vertreter/email/{email}` | Vertreter nach Email |
| `GET` | `/aggregates/vertreter/count` | Anzahl Vertreter |

## Beispiele

### Event speichern

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "id": "550e8400-e29b-41d4-a716-446655440000",
    "source": "/vertreter-service",
    "type": "space.maatini.vertreter.updated",
    "subject": "v001",
    "data": {"id": "v001", "name": "Max Mustermann", "email": "max@example.com"}
  }'
```

### Vertreter abrufen

```bash
curl http://localhost:8080/aggregates/vertreter/v001
```

## Architektur

Das System setzt auf eine **Reactive Event Sourcing** Architektur mit asynchronen Java-Projektionen.

```mermaid
graph TD
    subgraph "Write Side (Command)"
        API_W[REST API<br/>(EventResource)] -->|POST /events| ES[EventService]
        ES -->|INSERT| DB_Events[(PostgreSQL<br/>events table)]
    end

    subgraph "Database Layer"
        DB_Events -->|TRIGGER (After Insert)| DB_Notify[NOTIFY events_channel]
        DB_Events -.->|SELECT Unprocessed| PROJ
        PROJ -->|UPSERT| DB_Agg[(PostgreSQL<br/>vertreter_aggregate)]
    end

    subgraph "Async Projection (Java)"
        DB_Notify -.->|LISTEN| PROJ[VertreterProjectorService]
        PROJ -->|Logic / Apply Event| PROJ
    end

    subgraph "Read Side (Query)"
        API_R[REST API<br/>(VertreterAggregateResource)] -->|GET /aggregates| AS[VertreterAggregateService]
        AS -->|SELECT| DB_Agg
    end

    classDef java fill:#2C3E50,stroke:#fff,stroke-width:2px,color:#fff;
    classDef db fill:#27AE60,stroke:#fff,stroke-width:2px,color:#fff;
    classDef api fill:#E67E22,stroke:#fff,stroke-width:2px,color:#fff;

    class API_W,API_R,ES,AS,PROJ java;
    class DB_Events,DB_Agg,DB_Notify db;
```

### Komponenten

1.  **Event Ingestion**: Events werden per REST empfangen und idempotenz-sicher in die `events`-Tabelle geschrieben.
2.  **Notification**: Ein minimaler PostgreSQL-Trigger sendet ein `NOTIFY`-Signal, sobald ein neues Event eingefügt wird.
3.  **Async Projection (Java)**: Der `VertreterProjectorService` lauscht auf diesen Kanal (Reactive PostgreSQL Client), lädt unverarbeitete Events, wendet die Geschäftslogik an und aktualisiert das Read-Model (`vertreter_aggregate`).
4.  **Query**: Abfragen lesen ausschließlich vom optimierten Read-Model.

## Anwendungsfälle

Dieser Architektur-Ansatz bietet:

*   **Near-Realtime Updates**: Durch `LISTEN/NOTIFY` reagiert das System millisekundenschnell auf neue Events, ohne Polling-Overhead.
*   **Revisionssicherheit**: Unveränderlicher Audit-Log aller Änderungen in der `events`-Tabelle.
*   **Performance**: Trennung von Schreib- (Events) und Leselast (Aggregates).
*   **Flexibilität**: Die Projektionslogik ist in Java implementiert und kann komplexe Regeln abbilden, externe Services aufrufen oder E-Mails versenden.
*   **Reply-Fähigkeit**: Durch Löschen des Read-Models und Zurücksetzen des `processed_at`-Status kann die gesamte Datenbank aus den Events neu aufgebaut werden.

## Event-Verarbeitung

### Verarbeitungsreihenfolge
Events werden sequenziell anhand ihres Zeitstempels (`created_at`) verarbeitet, um Konsistenz zu garantieren.

### Fehlerbehandlung & Idempotenz
Der Projektor speichert den Fortschritt in der `events`-Tabelle (`processed_at`). Startet der Service neu, setzt er automatisch an der letzten Position fort.


## Tests

Das Projekt verfügt über eine umfassende Test-Suite mit Integrationstests, Validierungs-Tests und Tests für die PostgreSQL-Trigger-Logik.

### Tests ausführen

```bash
# Alle Tests ausführen
./mvnw test

# Spezifischen Test ausführen
./mvnw test -Dtest=VertreterAggregateResourceTest
```

### Code Coverage

Das Projekt verwendet [JaCoCo](https://www.eclemma.org/jacoco/) zur Ermittlung der Code Coverage.

Der Report wird automatisch bei der Testausführung generiert:
```bash
./mvnw test
```

Der HTML-Report befindet sich unter: `target/jacoco-report/index.html`

| Komponenten | Abdeckung |
|-------------|-----------|
| **EventResource** | REST API, Validierung, Edge Cases, Idempotenz |
| **VertreterAggregateResource** | Aggregat-Abfragen, PostgreSQL-Trigger-Logik |
| **Data Transfer Objects** | Validierungs-Annotationen, Mapper |

| **Data Transfer Objects** | Validierungs-Annotationen, Mapper |

Die Tests verwenden [Testcontainers](https://www.testcontainers.org/) bzw. Dev-Services, um automatisch eine PostgreSQL-Instanz für die Tests zu starten.

### Performance Benchmarking

Für Lasttests wird [k6](https://k6.io/) verwendet. Ein vorbereitetes Skript befindet sich in `benchmarks/load-test.js`.

**Benchmark ausführen:**
```bash
# App muss laufen (./mvnw quarkus:dev)
devbox run k6 run benchmarks/load-test.js
```

## Features

### Vertretene Person (Erweiterung)

Das Vertreter-Event wurde erweitert und unterstützt nun auch eine "vertretene Person":
```json
{
  ...
  "data": {
    "id": "v001",
    "name": "Max Mustermann", 
    "vertretenePerson": {
       "id": "p001",
       "name": "Erika Musterfrau"
    }
  }
}
```
Die Aggregation speichert diese Informationen automatisch in den Spalten `vertretene_person_id` und `vertretene_person_name`.

## Event-Typen

| Typ | Beschreibung |
|-----|--------------|
| `space.maatini.vertreter.created` | Neuer Vertreter |
| `space.maatini.vertreter.updated` | Vertreter aktualisiert |
| `space.maatini.vertreter.deleted` | Vertreter gelöscht |

## Devbox Befehle

| Befehl | Beschreibung |
|--------|--------------|
| `devbox run pg:start` | PostgreSQL starten |
| `devbox run pg:stop` | PostgreSQL stoppen |
| `devbox run pg:create` | Datenbank erstellen |
| `devbox run pg:psql` | PostgreSQL CLI |

---

## Erweiterung um neue Event-Typen

Um eine neue Event-Art (z.B. "Abwesenheit") hinzuzufügen, sind folgende Schritte notwendig:

### Schritt 1: Aggregat-Tabelle erstellen

Neue Flyway-Migration anlegen (z.B. `V6__create_abwesenheit_aggregate.sql`):

```sql
CREATE TABLE abwesenheit_aggregate (
    id VARCHAR(255) PRIMARY KEY,
    mitarbeiter_id VARCHAR(255) NOT NULL,
    von DATE NOT NULL,
    bis DATE NOT NULL,
    grund VARCHAR(100),  -- z.B. 'urlaub', 'krank', 'fortbildung'
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    event_id UUID REFERENCES events(id),
    version INTEGER NOT NULL DEFAULT 1
);

CREATE INDEX idx_abwesenheit_mitarbeiter ON abwesenheit_aggregate(mitarbeiter_id);
CREATE INDEX idx_abwesenheit_zeitraum ON abwesenheit_aggregate(von, bis);
```

### Schritt 2: Aggregations-Trigger erstellen

In derselben oder einer neuen Migration:

```sql
CREATE OR REPLACE FUNCTION aggregate_abwesenheit()
RETURNS TRIGGER AS $$
BEGIN
    IF NEW.type LIKE 'space.maatini.abwesenheit.%' THEN
        CASE NEW.type
            WHEN 'space.maatini.abwesenheit.created', 'space.maatini.abwesenheit.updated' THEN
                INSERT INTO abwesenheit_aggregate (id, mitarbeiter_id, von, bis, grund, updated_at, event_id, version)
                VALUES (
                    NEW.data->>'id',
                    NEW.data->>'mitarbeiterId',
                    (NEW.data->>'von')::DATE,
                    (NEW.data->>'bis')::DATE,
                    NEW.data->>'grund',
                    COALESCE(NEW.time, NOW()),
                    NEW.id,
                    1
                )
                ON CONFLICT (id) DO UPDATE SET
                    mitarbeiter_id = COALESCE(EXCLUDED.mitarbeiter_id, abwesenheit_aggregate.mitarbeiter_id),
                    von = COALESCE(EXCLUDED.von, abwesenheit_aggregate.von),
                    bis = COALESCE(EXCLUDED.bis, abwesenheit_aggregate.bis),
                    grund = COALESCE(EXCLUDED.grund, abwesenheit_aggregate.grund),
                    updated_at = EXCLUDED.updated_at,
                    event_id = EXCLUDED.event_id,
                    version = abwesenheit_aggregate.version + 1;
                    
                -- Mark as processed
                UPDATE events SET processed_at = NOW() WHERE id = NEW.id;
                    
            WHEN 'space.maatini.abwesenheit.deleted' THEN
                DELETE FROM abwesenheit_aggregate WHERE id = NEW.data->>'id';
                UPDATE events SET processed_at = NOW() WHERE id = NEW.id;
        END CASE;
    END IF;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_aggregate_abwesenheit
    AFTER INSERT ON events
    FOR EACH ROW
    EXECUTE FUNCTION aggregate_abwesenheit();
```

### Schritt 3: Java-Komponenten erstellen (optional)

Für typisierte Abfragen:

1. **Entity** (`AbwesenheitAggregate.java`)
2. **DTO** (`AbwesenheitDTO.java`)
3. **Service** (`AbwesenheitAggregateService.java`)
4. **Resource** (`AbwesenheitAggregateResource.java`)

### Schritt 4: Events senden

```bash
curl -X POST http://localhost:8080/events \
  -H "Content-Type: application/json" \
  -d '{
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "source": "/hr-service",
    "type": "space.maatini.abwesenheit.created",
    "subject": "abs-001",
    "data": {
      "id": "abs-001",
      "mitarbeiterId": "m001",
      "von": "2026-03-01",
      "bis": "2026-03-15",
      "grund": "urlaub"
    }
  }'
```

### ⚡ Wichtig: Kein Redeployment nötig!

Die Aggregationslogik liegt in PostgreSQL. Nach Ausführen der SQL-Migration werden neue Events sofort verarbeitet – **ohne die Anwendung neu zu starten**.

Nur für typisierte Java-Abfragen (REST-Endpoints) ist ein Deployment notwendig.
