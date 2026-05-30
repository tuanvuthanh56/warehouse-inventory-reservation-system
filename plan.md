# Implementation Plan - Warehouse Inventory Reservation Microservices

## 1. Mục tiêu của plan

Plan này dùng để xây dựng hệ thống Warehouse Inventory Reservation theo hướng microservices hoàn chỉnh, bám sát requirement và thiết kế trong `microservices-saga-design.md`.

Mục tiêu cuối cùng:

- Có 2 backend services chạy độc lập: Reservation Service và Inventory Service.
- Dùng Java 17, Spring Boot 3.
- Có REST API hoạt động đúng requirement.
- Có database modelling và Flyway migrations.
- Có Clean Architecture rõ ràng.
- Có State Pattern và Factory Pattern trong code, được giải thích trong README.
- Có Saga orchestration giữa Reservation Service và Inventory Service.
- Có Outbox/Inbox hoặc cơ chế tương đương để đảm bảo message reliability/idempotency.
- Có unit tests và integration/concurrency tests.
- Không oversell inventory khi nhiều request đồng thời.
- Có README rõ ràng để reviewer chạy được toàn bộ hệ thống.

## 2. Target Architecture

```text
warehouse-inventory-reservation-system/
  README.md
  plan.md
  microservices-saga-design.md
  docker-compose.yml
  pom.xml hoặc build.gradle
  common/
  reservation-service/
  inventory-service/
```

Recommended modules:

- `common`: shared message contracts, common error model, test utilities nếu cần.
- `reservation-service`: REST API và saga orchestrator.
- `inventory-service`: stock owner, inventory hold, concurrency control.

Nếu dùng Maven, nên dùng multi-module Maven:

```text
root pom.xml
  common
  reservation-service
  inventory-service
```

Nếu dùng Gradle, nên dùng multi-project Gradle:

```text
settings.gradle
build.gradle
common/build.gradle
reservation-service/build.gradle
inventory-service/build.gradle
```

Khuyến nghị: dùng Maven nếu muốn đơn giản, phổ biến và dễ review trong take-home.

## 3. Phase 1 - Foundation, Common, DB, Migrations, Layers

### 3.1 Deliverables

Kết thúc phase này cần có:

- Project structure hoàn chỉnh.
- 2 Spring Boot apps build được.
- Common module build được.
- Docker Compose cho infrastructure local.
- PostgreSQL riêng cho từng service.
- Kafka hoặc RabbitMQ cho messaging.
- Flyway migrations cho cả 2 service.
- Package/layer structure theo Clean Architecture.
- Common error response model.
- Health check endpoint.
- Basic CI-friendly commands trong README draft hoặc notes.

### 3.2 Step-by-step

#### Step 1: Khởi tạo multi-module project

Tạo cấu trúc:

```text
common/
reservation-service/
inventory-service/
```

Cấu hình:

- Java 17.
- Spring Boot 3.x.
- Spring Web.
- Spring Validation.
- Spring Data JPA.
- PostgreSQL Driver.
- Flyway.
- Lombok nếu muốn, nhưng không bắt buộc.
- Test dependencies: JUnit 5, AssertJ, Mockito, Testcontainers.

Acceptance criteria:

- Root project build thành công.
- Từng service chạy được app context rỗng.
- Không hard-code secret trong source.

#### Step 2: Tạo docker-compose

Tạo local infrastructure:

- `reservation-db`
- `inventory-db`
- `kafka` hoặc `rabbitmq`
- optional: `kafka-ui` hoặc management UI nếu dùng RabbitMQ

Acceptance criteria:

- `docker compose up -d` chạy được.
- 2 database accessible từ app config.
- Broker accessible từ cả 2 services.

#### Step 3: Tạo Clean Architecture layers

Reservation Service:

```text
api/
application/
domain/
infrastructure/
```

Inventory Service:

```text
api/
application/
domain/
infrastructure/
```

Quy tắc dependency:

- `domain` không phụ thuộc Spring/JPA/messaging.
- `application` phụ thuộc domain và ports.
- `infrastructure` implement ports.
- `api` gọi application services.

Acceptance criteria:

- Package rõ ràng.
- Domain model không dùng JPA annotation nếu muốn clean hơn.
- Nếu dùng JPA entity trực tiếp, phải giữ code đơn giản và giải thích trade-off trong README.

#### Step 4: Common module

Tạo shared contracts:

- `ReserveInventoryCommand`
- `ConfirmInventoryCommand`
- `ReleaseInventoryCommand`
- `InventoryReservedEvent`
- `InventoryReservationRejectedEvent`
- `InventoryConfirmedEvent`
- `InventoryReleasedEvent`
- `ReservationItemMessage`
- `UnavailableItemMessage`

Tạo shared API/error model nếu phù hợp:

- `ApiErrorResponse`
- `ErrorCode`

Acceptance criteria:

- Common module không phụ thuộc vào service cụ thể.
- Message contract có `messageId`, `reservationId`, `orderId`, `occurredAt`.
- Contract dùng immutable style nếu có thể.

#### Step 5: Reservation Service migrations

Tạo migrations:

- `reservations`
- `reservation_items`
- `outbox_events`
- `inbox_messages`

Schema tối thiểu:

```text
reservations:
  id UUID PK
  order_id VARCHAR UNIQUE NOT NULL
  status VARCHAR NOT NULL
  failure_reason TEXT NULL
  version BIGINT NOT NULL
  created_at TIMESTAMP NOT NULL
  updated_at TIMESTAMP NOT NULL

reservation_items:
  id UUID PK
  reservation_id UUID NOT NULL
  sku VARCHAR NOT NULL
  quantity INT NOT NULL
  created_at TIMESTAMP NOT NULL

outbox_events:
  id UUID PK
  aggregate_type VARCHAR NOT NULL
  aggregate_id UUID NOT NULL
  event_type VARCHAR NOT NULL
  payload JSONB hoặc TEXT NOT NULL
  status VARCHAR NOT NULL
  created_at TIMESTAMP NOT NULL
  published_at TIMESTAMP NULL

inbox_messages:
  message_id UUID PK
  message_type VARCHAR NOT NULL
  processed_at TIMESTAMP NOT NULL
```

Acceptance criteria:

- Flyway migrate thành công trên reservation DB.
- Index/unique constraint cho `order_id`.
- Unique/index cho `reservation_items(reservation_id, sku)`.

#### Step 6: Inventory Service migrations

Tạo migrations:

- `products`
- `inventory`
- `inventory_holds`
- `inventory_hold_items`
- `outbox_events`
- `inbox_messages`

Schema tối thiểu:

```text
products:
  sku VARCHAR PK
  name VARCHAR NOT NULL
  description TEXT NULL
  created_at TIMESTAMP NOT NULL
  updated_at TIMESTAMP NOT NULL

inventory:
  sku VARCHAR PK
  on_hand_stock INT NOT NULL
  available_stock INT NOT NULL
  reserved_stock INT NOT NULL
  version BIGINT NOT NULL
  updated_at TIMESTAMP NOT NULL

inventory_holds:
  id UUID PK
  reservation_id UUID UNIQUE NOT NULL
  order_id VARCHAR NOT NULL
  status VARCHAR NOT NULL
  created_at TIMESTAMP NOT NULL
  updated_at TIMESTAMP NOT NULL

inventory_hold_items:
  id UUID PK
  hold_id UUID NOT NULL
  sku VARCHAR NOT NULL
  quantity INT NOT NULL
  created_at TIMESTAMP NOT NULL
```

Acceptance criteria:

- Flyway migrate thành công trên inventory DB.
- `inventory.reserved_stock`, `available_stock`, `on_hand_stock` có check constraint `>= 0`.
- Unique/index cho `inventory_holds(reservation_id)`.
- Unique/index cho `inventory_hold_items(hold_id, sku)`.

#### Step 7: Seed data cho demo/test

Thêm seed data qua migration hoặc profile dev:

```text
products:
  A100
  B200

inventory:
  A100: on_hand=100, available=100, reserved=0
  B200: on_hand=50, available=50, reserved=0
```

Acceptance criteria:

- Sau khi chạy app local, có thể query stock `A100`.

#### Step 8: Global error handling

Tạo:

- `ApiException`
- `NotFoundException`
- `ConflictException`
- `ValidationException` nếu cần
- `@RestControllerAdvice`

Response format:

```json
{
  "code": "RESERVATION_NOT_FOUND",
  "message": "Reservation not found.",
  "details": {},
  "traceId": "..."
}
```

Acceptance criteria:

- Validation error trả `400`.
- Not found trả `404`.
- Business conflict trả `409`.
- Error response thống nhất ở cả 2 services.

### 3.3 Phase 1 Definition of Done

Phase 1 hoàn thành khi:

- `docker compose up -d` chạy được infrastructure.
- Cả 2 services start được.
- Flyway migrate thành công cho cả 2 DB.
- Common module compile được.
- Có skeleton layers rõ ràng.
- Có global error handler.
- Có ít nhất 1 smoke test app context cho mỗi service.

## 4. Phase 2 - Business Logic, REST API, Unit Tests

### 4.1 Deliverables

Kết thúc phase này cần có:

- Reservation Service API hoạt động.
- Inventory Service stock query API hoạt động.
- Inventory reserve/confirm/release command handlers hoạt động.
- Reservation saga event handlers hoạt động.
- State Pattern implement cho reservation transitions.
- Factory Pattern implement cho reservation creation.
- Unit tests cho domain/application logic.
- Concurrency logic trong Inventory Service đã được test ở mức integration hoặc repository-level.

## 4.2 Reservation Service Implementation

### Step 1: Domain model

Tạo:

- `Reservation`
- `ReservationItem`
- `ReservationStatus`
- `ReservationStateMachine` hoặc các class state theo State Pattern
- `ReservationFactory`

Statuses:

```text
RESERVING
PENDING
CONFIRMING
CONFIRMED
CANCELLING
CANCELLED
REJECTED
FAILED_RETRYABLE
```

Business rules:

- Create reservation bắt đầu ở `RESERVING`.
- `RESERVING -> PENDING` khi Inventory reserved.
- `RESERVING -> REJECTED` khi Inventory rejected.
- `PENDING -> CONFIRMING` khi client confirm.
- `CONFIRMING -> CONFIRMED` khi Inventory confirmed.
- `PENDING -> CANCELLING` khi client cancel.
- `CANCELLING -> CANCELLED` khi Inventory released.
- Không cho cancel reservation đã `CONFIRMED`.
- Không cho confirm nếu reservation không phải `PENDING`.

Acceptance criteria:

- State transition invalid ném business exception.
- Factory validate request và tạo domain object hợp lệ.

### Step 2: Persistence

Tạo repository/adapter:

- Save reservation.
- Find by id.
- Find by orderId.
- Update status với optimistic version nếu cần.
- Save reservation items.

Acceptance criteria:

- Duplicate `orderId` không tạo reservation thứ hai.
- Repository mapping rõ ràng.

### Step 3: Create reservation use case

Flow:

```text
validate request
if orderId exists -> return existing reservation
factory creates reservation
save reservation + items
save ReserveInventoryCommand to outbox in same transaction
return reservation status RESERVING
```

HTTP:

```text
POST /api/v1/reservations -> 202 Accepted
```

Acceptance criteria:

- Request empty items trả `400`.
- Quantity <= 0 trả `400`.
- Duplicate SKU được merge hoặc reject rõ ràng. Khuyến nghị: merge duplicate SKU.
- Duplicate orderId trả reservation đã có.

### Step 4: Get reservation API

HTTP:

```text
GET /api/v1/reservations/{id}
```

Acceptance criteria:

- Found trả reservation + items.
- Not found trả `404`.

### Step 5: Confirm reservation API

Flow:

```text
find reservation
transition PENDING -> CONFIRMING
save ConfirmInventoryCommand to outbox in same transaction
return 202
```

Acceptance criteria:

- `PENDING` confirm thành `CONFIRMING`.
- Non-PENDING trả `409`.
- Not found trả `404`.

### Step 6: Cancel reservation API

Flow:

```text
find reservation
transition PENDING -> CANCELLING
save ReleaseInventoryCommand to outbox in same transaction
return 202
```

Acceptance criteria:

- `PENDING` cancel thành `CANCELLING`.
- `CONFIRMED` cancel trả `409`.
- Non-PENDING trả `409`.
- Not found trả `404`.

### Step 7: Reservation event handlers

Handle:

- `InventoryReservedEvent`: `RESERVING -> PENDING`
- `InventoryReservationRejectedEvent`: `RESERVING -> REJECTED`, save failure reason
- `InventoryConfirmedEvent`: `CONFIRMING -> CONFIRMED`
- `InventoryReleasedEvent`: `CANCELLING -> CANCELLED`

Acceptance criteria:

- Duplicate event không process lại nhờ inbox.
- Event đến sai state không phá dữ liệu. Có thể ignore idempotently hoặc log warning.

## 4.3 Inventory Service Implementation

### Step 1: Domain model

Tạo:

- `Product`
- `Inventory`
- `InventoryHold`
- `InventoryHoldItem`
- `InventoryHoldStatus`

Statuses:

```text
HELD
CONFIRMED
RELEASED
```

Business rules:

- Reserve chỉ thành công nếu tất cả SKU đủ `available_stock`.
- Nếu một SKU thiếu, reject toàn bộ command.
- Confirm chỉ áp dụng cho hold `HELD`.
- Release chỉ áp dụng cho hold `HELD`.
- Duplicate command không được double deduct/double release/double confirm.

### Step 2: Stock query API

HTTP:

```text
GET /api/v1/inventory/{sku}
```

Acceptance criteria:

- Found trả `sku`, `onHandStock`, `availableStock`, `reservedStock`.
- Not found trả `404`.

### Step 3: Reserve inventory command handler

Flow:

```text
check inbox messageId
if reservation_id already has hold:
  return/publish current result idempotently
begin transaction
lock inventory rows by SKU ordered by SKU
if any SKU missing or insufficient:
  save InventoryReservationRejectedEvent to outbox
  save inbox message
  commit
else:
  decrease available_stock
  increase reserved_stock
  create inventory_hold HELD
  create inventory_hold_items
  save InventoryReservedEvent to outbox
  save inbox message
  commit
```

Recommended SQL locking:

```sql
SELECT *
FROM inventory
WHERE sku IN (:skus)
ORDER BY sku
FOR UPDATE;
```

Acceptance criteria:

- Multi-SKU reserve atomic.
- Không oversell khi concurrent requests.
- Insufficient stock không trừ bất kỳ SKU nào.
- Duplicate command không double deduct.

### Step 4: Confirm inventory command handler

Flow:

```text
check inbox messageId
find hold by reservationId
if hold CONFIRMED -> publish/ignore idempotently
if hold RELEASED -> reject/log conflict
if hold HELD:
  lock hold/items/inventory rows
  decrease reserved_stock
  decrease on_hand_stock
  set hold CONFIRMED
  save InventoryConfirmedEvent to outbox
  save inbox message
```

Acceptance criteria:

- `HELD -> CONFIRMED`.
- Stock changes:
  - `reserved_stock` giảm.
  - `on_hand_stock` giảm.
  - `available_stock` giữ nguyên.
- Duplicate confirm không trừ stock lần hai.

### Step 5: Release inventory command handler

Flow:

```text
check inbox messageId
find hold by reservationId
if hold RELEASED -> publish/ignore idempotently
if hold CONFIRMED -> reject/log conflict
if hold HELD:
  lock hold/items/inventory rows
  increase available_stock
  decrease reserved_stock
  set hold RELEASED
  save InventoryReleasedEvent to outbox
  save inbox message
```

Acceptance criteria:

- `HELD -> RELEASED`.
- Stock changes:
  - `available_stock` tăng.
  - `reserved_stock` giảm.
  - `on_hand_stock` giữ nguyên.
- Duplicate release không cộng stock lần hai.

## 4.4 Messaging, Outbox, Inbox

### Step 1: Outbox publisher

Implement scheduled publisher hoặc broker integration:

```text
find NEW outbox_events
publish to broker
mark as PUBLISHED
```

Acceptance criteria:

- Outbox event publish được.
- Nếu publish fail, event vẫn còn để retry.
- Publisher không làm mất event khi app crash.

### Step 2: Inbox consumer guard

Mỗi message consumer:

```text
if messageId exists -> ignore
else process and insert inbox row in same transaction
```

Acceptance criteria:

- Duplicate message không gây duplicate business effect.

### Step 3: Topics/queues

Gợi ý topics:

```text
inventory.commands
inventory.events
```

Routing:

- Reservation Service publishes commands to `inventory.commands`.
- Inventory Service consumes `inventory.commands`.
- Inventory Service publishes events to `inventory.events`.
- Reservation Service consumes `inventory.events`.

Acceptance criteria:

- End-to-end async flow hoạt động.

## 4.5 Unit Tests

Reservation unit tests:

- Factory creates valid reservation.
- Factory rejects empty items.
- Factory rejects invalid quantity.
- Factory merges duplicate SKU hoặc rejects theo policy.
- State machine allows valid transitions.
- State machine rejects invalid transitions.
- Confirm non-PENDING throws conflict.
- Cancel CONFIRMED throws conflict.
- Event handler moves `RESERVING -> PENDING`.
- Event handler moves `RESERVING -> REJECTED`.

Inventory unit tests:

- Reserve succeeds when all SKU available.
- Reserve rejects when one SKU insufficient.
- Confirm HELD changes stock correctly.
- Release HELD changes stock correctly.
- Duplicate reserve does not double deduct.
- Duplicate confirm does not double deduct.
- Duplicate release does not double add.

### 4.6 Phase 2 Definition of Done

Phase 2 hoàn thành khi:

- Tất cả required REST APIs đã có.
- Business logic chính chạy được qua API/message.
- State Pattern và Factory Pattern đã hiện diện trong code.
- Unit tests pass.
- Inventory reserve logic có test chứng minh không oversell ở mức repository/integration.
- Outbox/Inbox basic flow hoạt động.

## 5. Phase 3 - Completion, README, Quality Gates, Requirement Checklist

### 5.1 Deliverables

Kết thúc phase này cần có:

- README hoàn chỉnh.
- Docker Compose chạy toàn hệ thống.
- End-to-end tests pass.
- Concurrency tests pass.
- Code cleanup.
- Requirement checklist pass 100%.

## 5.2 End-To-End Flows

### Flow 1: Create reservation success

Steps:

```text
POST /api/v1/reservations
poll GET /api/v1/reservations/{id}
expect status PENDING
GET /api/v1/inventory/A100
expect available reduced, reserved increased
```

Acceptance criteria:

- API trả `202` ban đầu.
- Sau async processing, reservation là `PENDING`.
- Inventory stock đúng.

### Flow 2: Create reservation rejected

Steps:

```text
POST /api/v1/reservations with quantity > available
poll GET /api/v1/reservations/{id}
expect status REJECTED
expect failureReason contains INSUFFICIENT_STOCK
```

Acceptance criteria:

- Không item nào bị trừ.
- Error/rejection rõ ràng.

### Flow 3: Confirm reservation

Steps:

```text
create reservation
wait PENDING
POST /api/v1/reservations/{id}/confirm
poll GET /api/v1/reservations/{id}
expect CONFIRMED
GET inventory
expect reserved decreased and onHand decreased
```

Acceptance criteria:

- `PENDING -> CONFIRMING -> CONFIRMED`.
- Cancel sau confirm trả `409`.

### Flow 4: Cancel reservation

Steps:

```text
create reservation
wait PENDING
POST /api/v1/reservations/{id}/cancel
poll GET /api/v1/reservations/{id}
expect CANCELLED
GET inventory
expect available restored and reserved decreased
```

Acceptance criteria:

- `PENDING -> CANCELLING -> CANCELLED`.
- Stock được trả lại.

## 5.3 Concurrency Tests

### Test 1: Many concurrent reservations for same SKU

Setup:

```text
A100 available = 100
20 concurrent requests
each request reserves 10
```

Expected:

```text
only 10 reservations eventually PENDING
remaining reservations eventually REJECTED
available_stock = 0
reserved_stock = 100
never negative stock
```

### Test 2: Multi-SKU atomicity

Setup:

```text
A100 available = 100
B200 available = 1
request reserves A100=10, B200=5
```

Expected:

```text
reservation REJECTED
A100 remains 100 available
B200 remains 1 available
no partial hold
```

### Test 3: Duplicate messages

Scenarios:

- Same `ReserveInventoryCommand` delivered twice.
- Same `ConfirmInventoryCommand` delivered twice.
- Same `ReleaseInventoryCommand` delivered twice.
- Same `InventoryReservedEvent` delivered twice.

Expected:

- No double stock deduction.
- No double stock release.
- Reservation state remains valid.

## 5.4 README Requirements

README phải có:

- Problem summary.
- Architecture overview.
- Why microservices + Saga.
- Service responsibilities.
- Tech stack:
  - Java 17
  - Spring Boot 3
  - PostgreSQL
  - Kafka/RabbitMQ
  - Flyway
  - JUnit/Testcontainers
- How to run:
  - prerequisites
  - `docker compose up -d`
  - command to run tests
  - command to start services
- API examples:
  - create reservation
  - get reservation
  - confirm
  - cancel
  - get stock
- Database migrations explanation.
- Concurrency safety explanation.
- Error handling format.
- Design patterns:
  - State Pattern: reservation lifecycle transitions.
  - Factory Pattern: reservation creation.
  - Saga Pattern: cross-service business transaction.
  - Outbox Pattern: reliable message publication.
  - Inbox Pattern: idempotent message consumption.
- Trade-offs:
  - eventual consistency
  - complexity vs scalability
  - why not distributed transaction
- Test strategy.

## 5.5 Code Quality Checklist

Clean Architecture:

- Domain không phụ thuộc controller/framework.
- Application services chứa use cases.
- Infrastructure chứa JPA/messaging implementation.
- API layer chỉ mapping HTTP <-> use case.

SOLID:

- Single Responsibility: mỗi class có một lý do thay đổi rõ ràng.
- Open/Closed: state transitions/message handlers dễ mở rộng.
- Dependency Inversion: application phụ thuộc ports, không phụ thuộc implementation.

Validation:

- Request body có validation annotations.
- Quantity phải > 0.
- `orderId` required.
- `items` non-empty.
- SKU required.

Error handling:

- Không expose stacktrace.
- Dùng error code rõ ràng.
- `400`, `404`, `409`, `500` hợp lý.

Persistence:

- Có migrations.
- Có constraints/indexes.
- Có transaction boundaries rõ ràng.
- Inventory locking rõ ràng.

Testing:

- Unit tests cho domain/application.
- Integration tests cho persistence và messaging.
- Concurrency tests cho stock.
- E2E tests hoặc manual scripts cho API flows.

Operations:

- App có health endpoint.
- Config qua environment variables.
- Logs có correlation/trace id nếu kịp.

## 5.6 Requirement Checklist

Đối chiếu trực tiếp với `requirement.md`:

- Clean architecture and SOLID principles: phải thể hiện qua package structure và README.
- At least 2 design patterns: State Pattern và Factory Pattern bắt buộc, có thể thêm Saga/Outbox/Inbox.
- Working REST API: tất cả endpoint required chạy được.
- Proper error handling: có `@RestControllerAdvice` và error response thống nhất.
- Database modelling with migrations: Flyway migrations cho cả 2 DB.
- Unit tests: có tests cho Reservation và Inventory domain/application logic.
- Concurrency safety: có test concurrent reserve chứng minh không oversell.
- Clear README: có hướng dẫn run/test/API/design.
- Reserve inventory: create reservation async, eventually `PENDING` hoặc `REJECTED`.
- Confirm reservation: chỉ `PENDING` được confirm.
- Cancel reservation: chỉ `PENDING` được cancel, `CONFIRMED` không cancel được.
- Get reservation: trả reservation và items.
- Get current stock: Inventory Service trả stock theo SKU.
- Database tables: products, inventory, reservations, reservation_items có đầy đủ, cộng thêm tables phục vụ microservices reliability.

## 6. Recommended Execution Order For Codex

Khi bắt đầu implement, Codex nên đi theo thứ tự này:

1. Tạo project skeleton và build config.
2. Tạo docker-compose.
3. Tạo migrations cho cả 2 services.
4. Tạo common message contracts.
5. Tạo domain model và state/factory cho Reservation.
6. Tạo domain model và stock operations cho Inventory.
7. Implement persistence adapters.
8. Implement Reservation REST APIs.
9. Implement Inventory stock API.
10. Implement Outbox/Inbox.
11. Implement message publisher/consumers.
12. Viết unit tests cho Reservation.
13. Viết unit tests cho Inventory.
14. Viết integration tests cho DB/migrations.
15. Viết concurrency tests cho Inventory.
16. Viết end-to-end tests hoặc scripts.
17. Hoàn thiện README.
18. Chạy toàn bộ tests.
19. Rà requirement checklist.
20. Cleanup code, logs, naming, formatting.

## 7. Final Definition of Done

Dự án được coi là hoàn thành khi:

- `docker compose up -d` chạy được infrastructure.
- Cả Reservation Service và Inventory Service start thành công.
- API create/get/confirm/cancel/get-stock chạy đúng.
- Reservation không bị oversell dưới concurrent requests.
- Multi-SKU reserve không bị partial deduction.
- Confirm/cancel thay đổi stock đúng.
- Duplicate message/request không tạo duplicate side effects.
- All tests pass.
- README đủ để reviewer tự chạy.
- README nêu rõ State Pattern và Factory Pattern nằm ở đâu.
- README giải thích Saga, Outbox/Inbox, idempotency và concurrency strategy.
