# Warehouse Inventory Reservation System

Microservices-based warehouse inventory reservation system built with Spring Boot.

The system is split into two services:

- `reservation-service`: exposes reservation APIs and orchestrates the reservation saga.
- `inventory-service`: owns SKU stock, inventory holds, and concurrency-safe stock updates.

Cross-service consistency is handled with Saga + Outbox + Inbox. Each service owns its own PostgreSQL database; there is no distributed transaction.

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Web, Validation, Data JPA, AMQP, Actuator
- OpenAPI / Swagger UI
- PostgreSQL per service
- RabbitMQ for async commands and events
- Flyway migrations
- JUnit 5, AssertJ, Mockito
- Docker Compose

## Project Structure

```text
.
|-- common/                    # Shared API error model and RabbitMQ message contracts
|-- reservation-service/       # Reservation API, saga orchestration, reservation persistence
|-- inventory-service/         # Inventory API, stock operations, hold persistence
|-- docker-compose.yml         # Local infrastructure and full-stack runtime
`-- pom.xml                    # Root Maven multi-module build
```

Each service follows the same package structure:

```text
api/             # REST controllers, DTOs, and API error handling
application/     # Use cases and orchestration logic
domain/          # Domain models, factories, state transitions, domain exceptions
infrastructure/  # Persistence, messaging, configuration, OpenAPI setup
```

## Architecture

```text
Client
  |
  v
Reservation Service
  | DB: reservations, reservation_items, outbox_events, inbox_messages
  | publishes Reserve/Confirm/ReleaseInventoryCommand
  v
RabbitMQ
  |
  v
Inventory Service
  | DB: inventory, inventory_holds, inventory_hold_items, outbox_events, inbox_messages
  | publishes InventoryReserved/Rejected/Confirmed/ReleasedEvent
  v
RabbitMQ
  |
  v
Reservation Service
```

Main patterns used:

- State Pattern: `ReservationStateMachine` controls valid reservation lifecycle transitions.
- Factory Pattern: `ReservationFactory` and `InventoryHoldFactory` validate and create domain objects.
- Saga Pattern: `ReservationApplicationService` coordinates the reservation workflow through inventory commands and events.
- Outbox Pattern: both services persist outgoing messages before publishing them to RabbitMQ.
- Inbox Pattern: both services store processed message IDs to avoid duplicate side effects.

## Local Run

Prerequisites:

- Java 17
- Maven 3.9+
- Docker Desktop

Build and test:

```bash
mvn clean test
mvn package
```

### Option A: Full Stack With Docker Compose

```bash
docker compose up --build
```

### Option B: Infrastructure in Docker, Services With Maven

Use this mode when editing Java code. Docker runs PostgreSQL and RabbitMQ; Maven runs the two Spring Boot services from source.

Start infrastructure:

```bash
docker compose up -d reservation-db inventory-db rabbitmq
```

Install the shared `common` module once from the repository root:

```bash
mvn -am -pl common install -DskipTests
```

Run Inventory Service:

```bash
mvn -f inventory-service/pom.xml spring-boot:run
```

Run Reservation Service in another terminal:

```bash
mvn -f reservation-service/pom.xml spring-boot:run
```

Local `.env` values are loaded automatically by Spring Boot. Use these files when custom local values are needed:

- `reservation-service/.env`
- `inventory-service/.env`

The committed `.env.example` files document the expected variables.

When `common` changes, reinstall it and restart both services:

```bash
mvn -am -pl common install -DskipTests
```

## Service URLs

- Reservation Service: `http://localhost:8081`
- Inventory Service: `http://localhost:8082`
- Reservation Swagger UI: `http://localhost:8081/swagger-ui/index.html`
- Inventory Swagger UI: `http://localhost:8082/swagger-ui/index.html`
- RabbitMQ UI: `http://localhost:15672` with `app` / `app`

Health checks:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

API request and response details are available through Swagger UI.

## Reservation Lifecycle

```text
RESERVING -> PENDING -> CONFIRMING -> CONFIRMED
RESERVING -> REJECTED
PENDING   -> CANCELLING -> CANCELLED
```

Only `PENDING` reservations can be confirmed or cancelled. `CONFIRMED`, `CANCELLED`, and `REJECTED` are terminal states for public behavior.

## Inventory Lifecycle

```text
HELD -> CONFIRMED
HELD -> RELEASED
```

Inventory stock rules:

- Reserve decreases `available_stock` and increases `reserved_stock`.
- Confirm decreases `reserved_stock` and `on_hand_stock`.
- Cancel/release increases `available_stock` and decreases `reserved_stock`.

Seed stock:

- `A100`: on hand 100, available 100, reserved 0
- `B200`: on hand 50, available 50, reserved 0

## Concurrency Safety

Inventory Service is the only stock owner. Reservation Service never changes stock directly.

For reserve operations, Inventory Service locks requested inventory rows in sorted SKU order with a pessimistic write lock. Concurrent reservations for the same SKU are serialized at the database row level. Multi-SKU reserve is handled in one transaction: if any SKU is missing or insufficient, no SKU is deducted.

## Error Format

Both services use a global exception handler to return a consistent JSON error response for validation errors, domain conflicts, missing resources, and unexpected failures. Each response includes an application error code, a readable message, optional details, a trace ID, and a timestamp.

```json
{
  "code": "RESERVATION_NOT_PENDING",
  "message": "Only PENDING reservations can be confirmed.",
  "details": {
    "reservationId": "..."
  },
  "traceId": "...",
  "timestamp": "..."
}
```

## Database Structure

Reservation Service database:

- `reservations`: reservation aggregate root with order ID, status, failure reason, and optimistic version.
- `reservation_items`: SKUs and quantities attached to each reservation.
- `outbox_events`: pending and published integration messages from Reservation Service.
- `inbox_messages`: processed inventory event IDs for idempotency.

Inventory Service database:

- `inventory`: stock counters per SKU: `on_hand_stock`, `available_stock`, and `reserved_stock`.
- `inventory_holds`: reservation-level hold records.
- `inventory_hold_items`: SKUs and quantities held for a reservation.
- `outbox_events`: pending and published integration messages from Inventory Service.
- `inbox_messages`: processed reservation command IDs for idempotency.

Flyway migration files live under:

- `reservation-service/src/main/resources/db/migration`
- `inventory-service/src/main/resources/db/migration`

## Tests

Run all automated tests:

```bash
mvn test
```

Current tests cover:

- Reservation factory validation and duplicate SKU merging.
- Inventory hold factory validation and duplicate SKU merging.
- Reservation state transitions and invalid transition rejection.
- Inventory stock reserve, confirm, and release arithmetic.
- Inventory reserve success for multi-SKU requests.
- Inventory reserve rejection without partial deduction.
- REST controller behavior, global error handlers, messaging config, listeners, and outbox publishers.

## Scalability and Reliability

Both services can run multiple instances behind a load balancer. Inventory correctness is protected by PostgreSQL row locks, so concurrent reservations for the same SKU are serialized by the database.

Outbox publishers use `FOR UPDATE SKIP LOCKED`, allowing multiple service instances to publish different outbox rows without claiming the same event. RabbitMQ listeners use retry with exponential backoff and dead-letter queues, so repeatedly failing messages are isolated instead of blocking normal traffic.
