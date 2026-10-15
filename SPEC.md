# Transactional Outbox Kafka Demo — Specification

## 1. Goal

Create a small Java demo of the flight booking example from the [AWS transactional outbox guidance](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html).

The demo focuses on one idea: a flight booking and its event are written to PostgreSQL in the same transaction. A separate process later publishes the event to Kafka.

PostgreSQL replaces Amazon RDS/Aurora and Kafka replaces Amazon SQS. Business logic is intentionally minimal.

## 2. Components

### Java services

1. `flight-service`
   - Accepts a flight booking request.
   - Writes the flight and outbox event in one PostgreSQL transaction.

2. `event-processing-service`
   - Polls the outbox table.
   - Publishes pending events to Kafka.
   - Deletes an event from the outbox after Kafka acknowledges it.

3. `payment-service`
   - Consumes flight events from Kafka.
   - Logs that it is processing a payment.
   - Does not implement real payment logic or store payment data.

### Infrastructure

- One PostgreSQL database.
- One single-node Kafka broker.
- One Kafka topic: `flight.events`.
- Docker Compose for the complete demo.

## 3. Architecture

```text
Client
  |
  | POST /flights
  v
flight-service
  |  one database transaction
  +----> flights
  +----> flight_outbox
              |
              | poll
              v
     event-processing-service
              |
              | publish
              v
       Kafka: flight.events
              |
              v
       payment-service
              |
              +----> log "Processing payment for flight ..."
```

## 4. Technology

- Java 21.
- Maven multi-module project.
- Spring Boot 3.x.
- Spring Web.
- Spring JDBC.
- Spring Kafka.
- Flyway.
- PostgreSQL.
- Docker Compose.
- JUnit and Testcontainers for a small number of focused tests.

Spring JDBC is preferred over a larger persistence model because the demo only needs two tables and a few SQL operations.

## 5. Flight service

Port: `8080`

### API

#### `POST /flights`

Request fields follow the AWS sample:

```json
{
  "departureAirport": "Paris",
  "arrivalAirport": "London",
  "departureDateTime": "2026-11-01T08:00:00Z",
  "arrivalDateTime": "2026-11-01T09:15:00Z"
}
```

Response: `201 Created`

```json
{
  "id": "9dce3485-5d42-4dc4-9f09-59a11bd63d90",
  "departureAirport": "Paris",
  "arrivalAirport": "London",
  "departureDateTime": "2026-11-01T08:00:00Z",
  "arrivalDateTime": "2026-11-01T09:15:00Z"
}
```

The service validates that all fields are present and that arrival is later than departure.

### Transaction

One `@Transactional` method:

1. Generates a flight ID and event ID.
2. Inserts the flight into `flights`.
3. Serializes a `FLIGHT_BOOKED` event.
4. Inserts the event into `flight_outbox`.
5. Commits both inserts together.

The service never sends directly to Kafka.

### Optional inspection endpoints

- `GET /flights/{flightId}` returns a stored flight.
- `GET /internal/outbox` returns pending outbox rows for demonstration purposes.

## 6. Database

Flyway creates the tables.

### `flights`

| Column | Type | Rules |
|---|---|---|
| `id` | `uuid` | primary key |
| `departure_airport` | `varchar(100)` | not null |
| `arrival_airport` | `varchar(100)` | not null |
| `departure_date_time` | `timestamptz` | not null |
| `arrival_date_time` | `timestamptz` | not null |
| `created_at` | `timestamptz` | not null |

### `flight_outbox`

| Column | Type | Rules |
|---|---|---|
| `id` | `bigserial` | primary key and publication order |
| `event_id` | `uuid` | not null, unique |
| `flight_id` | `uuid` | not null |
| `event_type` | `varchar(100)` | not null; `FLIGHT_BOOKED` |
| `payload` | `jsonb` | not null |
| `created_at` | `timestamptz` | not null |

If either insert fails, the transaction rolls back and neither row remains.

### `processed_events`

The payment service uses this small table to recognize Kafka redelivery:

| Column | Type | Rules |
|---|---|---|
| `event_id` | `uuid` | primary key; idempotency key |
| `processed_at` | `timestamptz` | not null |

The primary key guarantees that only the first attempt to claim an `eventId` succeeds. No payment table is required for this demo.

## 7. Event format

Kafka topic: `flight.events`

Kafka key: `flightId`

Kafka value:

```json
{
  "eventId": "4fe48fac-2dd4-4a67-9b2b-fef276f4465d",
  "eventType": "FLIGHT_BOOKED",
  "occurredAt": "2026-10-13T20:15:30.123Z",
  "flight": {
    "id": "9dce3485-5d42-4dc4-9f09-59a11bd63d90",
    "departureAirport": "Paris",
    "arrivalAirport": "London",
    "departureDateTime": "2026-11-01T08:00:00Z",
    "arrivalDateTime": "2026-11-01T09:15:00Z"
  }
}
```

## 8. Event processing service

The service has no business API. A scheduled method runs once per second:

1. Select up to 10 outbox rows ordered by `id`.
2. Publish each payload to `flight.events`, using `flight_id` as the Kafka key.
3. Wait for Kafka acknowledgement.
4. Delete the acknowledged row.
5. If publication fails, log the error and leave the row for the next poll.

The initial implementation runs one event processing instance, so distributed row claiming and parallel workers are out of scope.

The publisher is at least once. A crash after Kafka accepts a message but before the row is deleted can cause the same outbox row to be read and published again. Every publication retains the original `eventId`, allowing the payment service to recognize the duplicate.

## 9. Payment service

The payment service uses a single `@KafkaListener` with consumer group `payment-service`. The event's `eventId` is its idempotency key.

For every valid event:

1. Start a database transaction.
2. Insert `eventId` into `processed_events` using `INSERT ... ON CONFLICT DO NOTHING`.
3. If the insert succeeds, log:

```text
Processing payment for flight <flightId>: <departureAirport> -> <arrivalAirport>
```

4. If no row was inserted, log that the duplicate was skipped and perform no payment action.
5. Commit the database transaction.
6. Return successfully so Kafka can commit the consumer offset.

This provides idempotent handling during normal delivery and redelivery. A real payment state change would be written in the same transaction as `processed_events`, giving effectively-once business processing.

The demo's action is only a log line, and a log cannot participate in the database transaction. A crash after logging but before the transaction commits could therefore still produce a duplicate log after restart. The `processed_events` table demonstrates the correct idempotency structure even though the illustrative side effect is intentionally simple.

## 10. Expected behavior

### Normal flow

1. The client books a flight.
2. The `flights` and `flight_outbox` rows commit together.
3. The event processing service publishes the outbox event.
4. Kafka acknowledges the publication, so the event processing service deletes the outbox row.
5. The payment service claims the `eventId`, logs the payment action, and commits the processed-event marker.

### Kafka unavailable

1. The client can still book a flight.
2. The event stays in `flight_outbox`.
3. The event processing service logs publication failures.
4. After Kafka returns, the event is published and deleted.

### Transaction rollback

If the outbox insert fails, the flight insert also rolls back. No event is published.

### Duplicate delivery

If the event processing service crashes after publishing but before deleting the row, it can publish the same outbox event again after restart. Both Kafka records contain the same `eventId`.

The first delivery inserts that ID into `processed_events` and runs the payment action. On the duplicate delivery, the unique primary key prevents another insert, so the payment service skips the action. Thus publication remains at least once while the consumer's business handling is effectively once.

For this log-only demo, the narrow crash window described in section 9 still applies because console output is not transactional.

## 11. Proposed project layout

```text
.
├── README.md
├── SPEC.md
├── pom.xml
├── compose.yaml
├── flight-service/
│   ├── pom.xml
│   └── src/...
├── event-processing-service/
│   ├── pom.xml
│   └── src/...
└── payment-service/
    ├── pom.xml
    └── src/...
```

## 12. Tests

Keep the test suite focused:

- Flight service integration test: a booking creates both database rows.
- Flight service integration test: a forced outbox failure rolls back the flight row.
- Event processing test: a successful Kafka send deletes the outbox row.
- Event processing test: a failed Kafka send keeps the outbox row.
- Payment service test: a flight event produces the expected log-processing call.
- Payment service test: delivering the same `eventId` twice records it once and skips the second payment action.

## 13. Acceptance criteria

- `docker compose up --build` starts PostgreSQL, Kafka, and all three services.
- A documented `curl` command books a flight.
- The flight and outbox event are stored atomically.
- The separate event processing service publishes the event to `flight.events`.
- The payment service logs that it is processing the payment.
- Re-delivering the same `eventId` causes the payment service to skip the duplicate.
- The outbox row is deleted only after Kafka acknowledges the event.
- When Kafka is unavailable, bookings still succeed and events remain in the outbox.
- The README demonstrates the normal and Kafka-unavailable flows.

## 14. Explicit simplifications

- No real payment processing.
- No payment persistence.
- No dead-letter topic.
- No retries beyond the next scheduled outbox poll.
- No exponential backoff.
- No multiple relay instances.
- No shared event library.
- No authentication or authorization.
- No production monitoring setup.

These omissions keep the code centered on the transactional outbox pattern rather than surrounding production concerns.
