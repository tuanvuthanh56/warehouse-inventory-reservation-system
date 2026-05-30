# Warehouse Inventory Reservation System

Microservices implementation of the Warehouse Inventory Reservation take-home assignment.

The system has two Spring Boot services:

- `reservation-service`: public reservation API and saga orchestrator.
- `inventory-service`: stock owner, inventory holds, and concurrency-safe stock updates.

## Tech Stack

- Java 17
- Spring Boot 3
- Spring Web, Validation, Data JPA, AMQP, Actuator
- PostgreSQL per service
- RabbitMQ for async commands/events
- Flyway migrations
- JUnit 5, AssertJ, Mockito
- Docker Compose

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
  | DB: products, inventory, inventory_holds, inventory_hold_items, outbox_events, inbox_messages
  | publishes InventoryReserved/Rejected/Confirmed/ReleasedEvent
  v
RabbitMQ
  |
  v
Reservation Service
```

Each service owns its own database. There is no distributed transaction. Cross-service workflow is handled with Saga + Outbox + Inbox.

## Design Patterns

- State Pattern: `reservation-service/src/main/java/com/example/reservation/domain/state/ReservationStateMachine.java` controls valid lifecycle transitions.
- Factory Pattern: `reservation-service/src/main/java/com/example/reservation/domain/factory/ReservationFactory.java` validates and creates reservations with merged duplicate SKUs.
- Saga Pattern: `ReservationApplicationService` creates reservation lifecycle commands and reacts to inventory events.
- Outbox Pattern: both services persist messages to `outbox_events`; scheduled publishers deliver them to RabbitMQ.
- Inbox Pattern: both services store processed `messageId` values in `inbox_messages` to avoid duplicate side effects.

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

Run the full stack:

```bash
docker compose up --build
```

Service URLs:

- Reservation Service: `http://localhost:8081`
- Inventory Service: `http://localhost:8082`
- RabbitMQ UI: `http://localhost:15672` with `app` / `app`

Health checks:

```bash
curl http://localhost:8081/actuator/health
curl http://localhost:8082/actuator/health
```

## API Examples

Create reservation:

```bash
curl -i -X POST http://localhost:8081/api/v1/reservations \
  -H "Content-Type: application/json" \
  -d "{\"orderId\":\"ORD-1001\",\"items\":[{\"sku\":\"A100\",\"quantity\":5},{\"sku\":\"B200\",\"quantity\":3}]}"
```

The initial response is `202 Accepted` with status `RESERVING`. Poll the reservation until it becomes `PENDING` or `REJECTED`:

```bash
curl http://localhost:8081/api/v1/reservations/{reservationId}
```

Confirm a pending reservation:

```bash
curl -i -X POST http://localhost:8081/api/v1/reservations/{reservationId}/confirm
```

Cancel a pending reservation:

```bash
curl -i -X POST http://localhost:8081/api/v1/reservations/{reservationId}/cancel
```

Get stock:

```bash
curl http://localhost:8082/api/v1/inventory/A100
```

Seed stock:

- `A100`: on hand 100, available 100, reserved 0
- `B200`: on hand 50, available 50, reserved 0

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

Reserve decreases `available_stock` and increases `reserved_stock`.
Confirm decreases `reserved_stock` and `on_hand_stock`.
Cancel/release increases `available_stock` and decreases `reserved_stock`.

## Concurrency Safety

Inventory Service is the only stock owner. Reservation Service never changes stock directly.

For reserve, Inventory Service locks all requested inventory rows in sorted SKU order:

```sql
SELECT i
FROM InventoryEntity i
WHERE i.sku IN (:skus)
ORDER BY i.sku
```

The repository uses `PESSIMISTIC_WRITE`, so concurrent reservations for the same SKU serialize at the database row level. Multi-SKU reserve is handled in one transaction: if any SKU is missing or insufficient, no SKU is deducted.

## Error Format

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

## Database Migrations

Reservation Service migrations:

- `reservations`
- `reservation_items`
- `outbox_events`
- `inbox_messages`

Inventory Service migrations:

- `products`
- `inventory`
- `inventory_holds`
- `inventory_hold_items`
- `outbox_events`
- `inbox_messages`

## Tests

Run:

```bash
mvn test
```

Current automated tests cover:

- Reservation Factory validation and duplicate SKU merging.
- Reservation State Pattern transitions and invalid transition rejection.
- Inventory stock reserve/confirm/release arithmetic.
- Inventory application reserve success for multi-SKU.
- Inventory application reserve rejection without partial deduction.

Docker-based E2E verification should be run with Docker Desktop enabled:

1. `docker compose up --build`
2. Create reservation and poll until `PENDING`.
3. Confirm or cancel and poll until terminal state.
4. Check inventory stock after each flow.

## Trade-Offs

- The system uses eventual consistency between services, so creation returns `RESERVING` before inventory replies.
- Outbox publisher is a simple scheduled publisher, appropriate for take-home scale.
- JPA entities are used in the application layer to keep the implementation compact; domain rules remain isolated in factory/state-machine classes.
- RabbitMQ is used instead of Kafka to keep local setup smaller.

## Requirement Checklist

- Clean architecture style packages: `api`, `application`, `domain`, `infrastructure`.
- REST API implemented for create/get/confirm/cancel reservation and get stock.
- Flyway migrations model all required tables.
- State Pattern and Factory Pattern implemented and documented.
- Saga, Outbox, and Inbox implemented for cross-service reliability.
- Validation and global error handlers implemented in both services.
- Inventory reserve is transaction-bound and uses pessimistic row locking.
- Unit/application tests pass with `mvn test`.
