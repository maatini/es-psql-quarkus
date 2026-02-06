# es-psql-quarkus

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
    "type": "de.vertreter.updated",
    "subject": "v001",
    "data": {"id": "v001", "name": "Max Mustermann", "email": "max@example.com"}
  }'
```

### Vertreter abrufen

```bash
curl http://localhost:8080/aggregates/vertreter/v001
```

## Architektur

```
┌─────────────┐     ┌─────────────┐     ┌─────────────────────┐
│ REST API    │────▶│ EventService│────▶│ events (PostgreSQL) │
└─────────────┘     └─────────────┘     └──────────┬──────────┘
                                                    │ Trigger
                                                    ▼
┌─────────────┐     ┌─────────────┐     ┌─────────────────────┐
│ REST API    │◀────│ AggService  │◀────│ vertreter_aggregate │
└─────────────┘     └─────────────┘     └─────────────────────┘
```

## Anwendungsfälle

Dieser Architektur-Ansatz eignet sich besonders für:

*   **Revisionssicherheit (Audit-Log)**: Vollständige Historie aller Änderungen (wer, wann, was), unverzichtbar für Compliance-kritische Bereiche (z.B. Vertreter-Vollmachten).
*   **High-Performance CQRS**: Trennung von Schreib- (Events) und Leselast (Aggregates). Das Lesemodell kann für spezifische UI-Anforderungen optimiert werden.
*   **Zeitpunkt-Bezogene Abfragen**: Möglichkeit, den Zustand des Systems zu jedem beliebigen Zeitpunkt in der Vergangenheit zu rekonstruieren ("Time Travel").
*   **Entkopplung**: Andere Services können asynchron auf Events reagieren (z.B. per Kafka-Connect auf die `events`-Tabelle), ohne die interne Logik zu kennen.

## Dynamische Aggregation

Die Aggregationslogik befindet sich in PostgreSQL (`aggregate_vertreter()` Funktion).
Um die Logik zu ändern, SQL ausführen:

```sql
CREATE OR REPLACE FUNCTION aggregate_vertreter() ...
```

**Kein Redeployment der Anwendung nötig!**

## Event-Verarbeitung

### Verarbeitungsreihenfolge
Events werden in der **Einfügereihenfolge** verarbeitet (PostgreSQL-Garantie durch synchronen Trigger).

### Verarbeitungsstatus
Jedes Event hat ein `processed_at` Feld:
- `NULL` = Noch nicht verarbeitet
- Timestamp = Zeitpunkt der Verarbeitung

### Automatischer Replay
Bei Neustart des Services werden unverarbeitete Events automatisch nachverarbeitet:
```sql
SELECT replay_unprocessed_events();
```

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
| `de.vertreter.created` | Neuer Vertreter |
| `de.vertreter.updated` | Vertreter aktualisiert |
| `de.vertreter.deleted` | Vertreter gelöscht |

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
    IF NEW.type LIKE 'de.abwesenheit.%' THEN
        CASE NEW.type
            WHEN 'de.abwesenheit.created', 'de.abwesenheit.updated' THEN
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
                    
            WHEN 'de.abwesenheit.deleted' THEN
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
    "type": "de.abwesenheit.created",
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
