# Kafka Transactional Outbox Demo

This project is a small Java implementation of the flight/payment example from the [AWS transactional outbox pattern](https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html).

It uses three Spring Boot services:

- `flight-service` writes a flight and an outbox event in one PostgreSQL transaction.
- `event-processing-service` polls the outbox and publishes events to Kafka.
- `payment-service` consumes events, deduplicates them by `eventId`, and logs the simulated payment action.

See [SPEC.md](SPEC.md) for the design and failure semantics.

## Source organization

Each service keeps its application class at the package root and groups implementation code by responsibility:

```text
flight-service
├── controller/   REST controllers and exception handling
├── domain/       Flight domain record
├── dto/          API and event payloads
├── repository/   Flight and outbox SQL
└── service/      Transactional booking logic

event-processing-service
├── model/        Outbox row model
├── repository/   Outbox polling and deletion SQL
├── scheduler/    Scheduled polling trigger
└── service/      Kafka publication transaction

payment-service
├── dto/          Consumed event payload
├── listener/     Kafka listener
├── repository/   Processed-event idempotency SQL
└── service/      Payment event handling
```

## Prerequisites

- Docker with Docker Compose.
- `curl` for the example requests.

No local Java or Maven installation is required for the Docker workflow.

## Start the demo

```bash
docker compose up --build
```

Wait until the services have started, then book a flight:

```bash
curl --fail-with-body \
  -H 'Content-Type: application/json' \
  -d '{
    "departureAirport": "Paris",
    "arrivalAirport": "London",
    "departureDateTime": "2026-11-01T08:00:00Z",
    "arrivalDateTime": "2026-11-01T09:15:00Z"
  }' \
  http://localhost:8080/flights
```

The response contains the generated flight ID. Shortly afterward, the payment-service log contains a line like:

```text
Processing payment for flight 9dce3485-...: Paris -> London (eventId=4fe48fac-...)
```

Follow the relevant logs with:

```bash
docker compose logs -f event-processing-service payment-service
```

## Inspect the data

List flights:

```bash
docker compose exec postgres \
  psql -U outbox -d outbox_demo \
  -c 'TABLE flight_service.flights;'
```

List pending outbox events:

```bash
curl --fail-with-body http://localhost:8080/internal/outbox
```

List processed event IDs:

```bash
docker compose exec postgres \
  psql -U outbox -d outbox_demo \
  -c 'TABLE payment_service.processed_events;'
```

## Demonstrate Kafka being unavailable

Stop Kafka while leaving PostgreSQL and the services running:

```bash
docker compose stop kafka
```

Book another flight with the same `curl` request. The HTTP request still succeeds because the flight service only commits to PostgreSQL. The event remains visible at `GET /internal/outbox` while the event processor logs publication failures.

Restart Kafka:

```bash
docker compose start kafka
```

The event processor retries on its next poll. The payment service logs the action, and the event disappears from the outbox.

## Why duplicates are safe for the consumer

The relay publishes at least once. It can crash after Kafka accepts a record but before PostgreSQL commits the outbox delete. On restart, it publishes the same row again with the same `eventId`.

The payment service executes this PostgreSQL statement before its simulated payment action:

```sql
INSERT INTO processed_events (event_id, processed_at)
VALUES (?, ?)
ON CONFLICT (event_id) DO NOTHING;
```

Only the first delivery inserts a row. Later deliveries with the same `eventId` are logged as duplicates and skipped. In a real payment service, the processed-event marker and payment update would be committed in the same transaction.

This demo only logs the payment action. Console output cannot be part of a database transaction, so it is illustrative rather than a claim of true exactly-once logging.

## HTTP endpoints

| Service | Endpoint | Purpose |
|---|---|---|
| Flight | `POST /flights` | Book a flight and create its outbox event. |
| Flight | `GET /flights/{id}` | Read a flight. |
| Flight | `GET /internal/outbox` | Inspect pending events. |
| All | `GET /actuator/health` | Check service health on ports 8080, 8081, or 8082. |

## Run tests locally

If Maven is installed:

```bash
mvn test
```

The service images also compile and package the code during `docker compose build`.

## Stop and clean up

Stop containers while retaining PostgreSQL data:

```bash
docker compose down
```

Remove the demo containers and database volume:

```bash
docker compose down --volumes
```
