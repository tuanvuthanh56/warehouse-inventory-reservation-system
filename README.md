# Warehouse Inventory Reservation System

Microservices-based warehouse inventory reservation system built with Spring Boot.

This repository implements **Challenge 1: Warehouse Inventory Reservation System**. I chose this challenge because stock reservation is concurrency-sensitive by nature, so it is a good fit for demonstrating database locking, state transitions, idempotent messaging, and service boundaries.

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
- Liquibase migrations
- JUnit 5, AssertJ, Mockito
- Docker Compose

## Project Structure

```text
.
|-- common/                    # Shared API error model and RabbitMQ message contracts
|-- database/                  # Liquibase changelogs and migration image
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

## Design Patterns

Patterns used and where they appear:

- State Pattern: `ReservationStateMachine` controls valid reservation lifecycle transitions.
- Factory Pattern: `ReservationFactory` and `InventoryHoldFactory` validate and create domain objects.
- Saga Pattern: `ReservationApplicationService` coordinates the reservation workflow through inventory commands and events.
- Outbox Pattern: both services persist outgoing messages before publishing them to RabbitMQ.
- Inbox Pattern: both services store processed message IDs to avoid duplicate side effects.

`ReservationStateMachine` intentionally uses an explicit transition table instead of one concrete class per state. The async Saga introduces intermediate states such as `RESERVING`, `CONFIRMING`, and `CANCELLING`, so a finite state machine keeps transitions easier to audit and test while still centralizing lifecycle rules.

## SOLID Principles

- Single Responsibility: controllers only handle HTTP concerns, application services orchestrate use cases, domain factories validate creation rules, and infrastructure classes own persistence or messaging.
- Open/Closed: reservation creation is routed through `ReservationType`, and lifecycle transitions can be extended in `ReservationStateMachine` without spreading transition checks across controllers.
- Liskov Substitution: `StandardReservation` implements the `Reservation` domain interface and can be replaced by another reservation implementation with the same contract.
- Interface Segregation: API DTOs, messaging contracts, repositories, and domain models are kept separate so callers depend only on the shape they need.
- Dependency Inversion: application services receive repositories, publishers, and domain services through constructor injection rather than constructing infrastructure directly.

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

Docker Compose starts PostgreSQL and RabbitMQ, runs the Liquibase migration jobs to completion,
then starts the application services.

### Option B: Infrastructure in Docker, Services With Maven

Use this mode when editing Java code. Docker runs PostgreSQL and RabbitMQ; Maven runs both
Liquibase migrations and the two Spring Boot services from source.

Start infrastructure:

```bash
docker compose up -d reservation-db inventory-db rabbitmq
```

Run database migrations:

```bash
mvn -f database/pom.xml -P reservation liquibase:update
mvn -f database/pom.xml -P inventory liquibase:update
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

## Response Format

Every API response uses the required envelope with `data` and `error`.

Success response:

```json
{
  "data": {
    "id": "4d7e0d9f-2a85-4f41-9d94-7fbf0c6e7fd5",
    "orderId": "ORD-1001",
    "status": "PENDING"
  },
  "error": null
}
```

Error response:

```json
{
  "data": null,
  "error": {
    "code": "INSUFFICIENT_STOCK",
    "message": "SKU A100 has only 30 units available, 50 were requested",
    "details": {
      "sku": "A100",
      "availableStock": 30,
      "requestedQuantity": 50
    },
    "traceId": "...",
    "timestamp": "..."
  }
}
```

Both services use a global exception handler for validation errors, domain conflicts, missing resources, and unexpected failures. `traceId` comes from `X-Trace-Id` when supplied, otherwise from the servlet request ID.

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

Liquibase changelog files live under:

- `database/reservation/changelog`
- `database/inventory/changelog`

Applications do not auto-run migrations on startup. The migration image is built from
`database/Dockerfile` and is run as a separate job in Docker Compose. In production, the same
image can be run as a CI/Kubernetes job before rolling out the services.

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

What can break at larger scale:

- A single hot SKU becomes limited by one PostgreSQL row lock. This is correct but can reduce throughput for flash-sale style traffic.
- RabbitMQ backlog can grow if Inventory Service is slower than Reservation Service. Queue depth, DLQ count, and outbox age should be monitored.
- The current API exposes two service URLs. A production deployment should add an API gateway or BFF so clients do not need to know internal service topology.
- Outbox tables need retention or archival after successful publication to prevent unbounded growth.

## Trade-offs and Future Improvements

- The system uses Saga + Outbox + Inbox instead of a single synchronous transaction. This improves service autonomy and failure recovery, but reservation creation is eventually consistent and clients may briefly see `RESERVING`.
- `POST /api/v1/reservations` returns `201 Created` once the reservation resource is persisted, while inventory confirmation still completes asynchronously through events.
- The lifecycle uses a compact finite state machine rather than GoF State classes. This fits the async Saga flow better today; concrete state classes can be introduced later if each status gains richer behavior.
- Add Testcontainers integration tests for the full Docker-like path, especially concurrent reservation requests against PostgreSQL.
- Add an API gateway and client-facing aggregation endpoint for inventory and reservation reads.
