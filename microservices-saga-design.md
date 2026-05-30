# Warehouse Inventory Reservation System - Thiết Kế Microservices Với Saga

## 1. Mục tiêu

Tài liệu này mô tả cách implement bài toán Warehouse Inventory Reservation System theo hướng microservices hoàn chỉnh, có thể scale cả về codebase lẫn lưu lượng truy cập.

Trong thiết kế này, hệ thống không dùng distributed transaction kiểu 2-phase commit. Thay vào đó, hệ thống dùng Saga Pattern, Outbox Pattern, Inbox/Idempotency Pattern và compensation để đảm bảo:

- Không oversell stock khi nhiều request đến đồng thời.
- Reservation lifecycle rõ ràng.
- Lỗi giữa các service có thể recover/retry.
- Mỗi service sở hữu database riêng.
- Có thể scale từng service độc lập.

## 2. Service Boundaries

Hệ thống chia thành 2 core services:

### Reservation Service

Reservation Service là owner của order reservation lifecycle.

Trách nhiệm:

- Public API cho client:
  - `POST /api/v1/reservations`
  - `POST /api/v1/reservations/{id}/confirm`
  - `POST /api/v1/reservations/{id}/cancel`
  - `GET /api/v1/reservations/{id}`
- Tạo reservation.
- Quản lý trạng thái của reservation.
- Điều phối saga.
- Xử lý idempotency theo `orderId` hoặc `Idempotency-Key`.
- Nhận event từ Inventory Service để cập nhật reservation.

Reservation Service không được tự ý thay đổi stock.

### Inventory Service

Inventory Service là owner duy nhất của stock.

Trách nhiệm:

- Public/query API:
  - `GET /api/v1/inventory/{sku}`
- Internal command handlers:
  - Reserve inventory
  - Confirm inventory hold
  - Release inventory hold
- Đảm bảo concurrency safety khi trừ stock.
- Lưu inventory hold theo reservation.
- Đảm bảo idempotency cho các command từ Reservation Service.

Inventory Service không được tự ý thay đổi reservation lifecycle business state. Nó chỉ phát event về kết quả xử lý stock.

## 3. High-Level Architecture

```text
Client
  |
  | HTTP
  v
Reservation Service
  |  DB: reservations, reservation_items, outbox, inbox
  |
  | Event/Command bus
  v
Inventory Service
  |  DB: products, inventory, inventory_holds, inventory_hold_items, outbox, inbox
  |
  | Event bus
  v
Reservation Service
```

Recommended infrastructure, bám sát techstack của requirement:

- Java 17
- Spring Boot 3
- PostgreSQL per service
- Kafka hoặc RabbitMQ cho async messaging
- Flyway cho database migrations
- Testcontainers cho integration/concurrency tests
- OpenAPI/Swagger cho API docs

Nếu muốn đơn giản hóa trong take-home assignment, có thể dùng synchronous HTTP giữa 2 service ở phiên bản đầu. Tuy nhiên, vẫn nên giữ cùng model Saga + Outbox/Inbox trong code. Bản production-like nên dùng message broker.

## 4. Vì Sao Dùng Saga Thay Vì Rollback Transaction?

Trong monolith, có thể dùng 1 database transaction:

```text
BEGIN
deduct inventory
create reservation
COMMIT / ROLLBACK
```

Trong microservices, Reservation Service và Inventory Service có database riêng. Không có một transaction database chung để rollback cả hai service.

Vì vậy, hệ thống lớn thường dùng:

- Saga: một business transaction được chia thành nhiều local transactions.
- Compensation: nếu một bước sau fail, gửi command ngược để bù trừ bước trước.
- Retry: lỗi tạm thời sẽ được retry.
- Idempotency: retry không tạo duplicate effect.
- Outbox: event không bị mất nếu service crash sau khi commit DB.

## 5. Reservation Lifecycle

Requirement gốc chỉ có:

```text
PENDING -> CONFIRMED
PENDING -> CANCELLED
```

Trong microservices nên mở rộng internal state để biểu diễn quá trình saga:

```text
REQUESTED
  -> RESERVING
  -> PENDING
  -> CONFIRMING
  -> CONFIRMED

REQUESTED / RESERVING
  -> REJECTED

PENDING
  -> CANCELLING
  -> CANCELLED

CONFIRMING / CANCELLING
  -> FAILED_RETRYABLE
```

API response có thể ẩn bớt internal state nếu muốn:

- `REQUESTED`, `RESERVING` có thể trả về `PROCESSING`
- `PENDING`, `CONFIRMED`, `CANCELLED`, `REJECTED` là state business rõ ràng

Cho take-home assignment, nên expose state thật trong API để người review thấy saga lifecycle rõ ràng.

## 6. Inventory Hold Lifecycle

Inventory Service nên có lifecycle riêng cho stock hold:

```text
HELD
  -> CONFIRMED
HELD
  -> RELEASED
```

Ý nghĩa:

- `HELD`: stock đã được giữ cho reservation, available stock đã bị trừ.
- `CONFIRMED`: stock đã được chốt tiêu thụ, reserved stock giảm, on-hand stock giảm.
- `RELEASED`: stock đã được trả lại, available stock tăng lại, reserved stock giảm.

## 7. Stock Model

Khuyến nghị dùng 3 cột:

```text
on_hand_stock
available_stock
reserved_stock
```

Ví dụ:

```text
Initial:
on_hand_stock = 100
available_stock = 100
reserved_stock = 0

Reserve 30:
on_hand_stock = 100
available_stock = 70
reserved_stock = 30

Confirm 30:
on_hand_stock = 70
available_stock = 70
reserved_stock = 0

Cancel 30:
on_hand_stock = 100
available_stock = 100
reserved_stock = 0
```

Invariant cần đảm bảo:

```text
available_stock >= 0
reserved_stock >= 0
on_hand_stock >= 0
available_stock + reserved_stock <= on_hand_stock
```

Sau khi confirm, `on_hand_stock` giảm vì hàng đã được tiêu thụ.

## 8. Database Design

### Reservation Service Database

#### reservations

```text
id UUID primary key
order_id VARCHAR unique not null
status VARCHAR not null
failure_reason TEXT null
version BIGINT not null
created_at TIMESTAMP not null
updated_at TIMESTAMP not null
```

`order_id` unique giúp chống tạo duplicate reservation cho cùng order.

#### reservation_items

```text
id UUID primary key
reservation_id UUID not null
sku VARCHAR not null
quantity INT not null
created_at TIMESTAMP not null
```

Unique khuyến nghị:

```text
unique(reservation_id, sku)
```

#### outbox_events

```text
id UUID primary key
aggregate_type VARCHAR not null
aggregate_id UUID not null
event_type VARCHAR not null
payload JSONB not null
status VARCHAR not null
created_at TIMESTAMP not null
published_at TIMESTAMP null
```

#### inbox_messages

```text
message_id UUID primary key
message_type VARCHAR not null
processed_at TIMESTAMP not null
```

### Inventory Service Database

#### products

```text
sku VARCHAR primary key
name VARCHAR not null
description TEXT null
created_at TIMESTAMP not null
updated_at TIMESTAMP not null
```

#### inventory

```text
sku VARCHAR primary key references products(sku)
on_hand_stock INT not null
available_stock INT not null
reserved_stock INT not null
version BIGINT not null
updated_at TIMESTAMP not null
```

#### inventory_holds

```text
id UUID primary key
reservation_id UUID unique not null
order_id VARCHAR not null
status VARCHAR not null
created_at TIMESTAMP not null
updated_at TIMESTAMP not null
```

`reservation_id` unique để command reserve retry không tạo hold mới.

#### inventory_hold_items

```text
id UUID primary key
hold_id UUID not null
sku VARCHAR not null
quantity INT not null
created_at TIMESTAMP not null
```

Unique khuyến nghị:

```text
unique(hold_id, sku)
```

#### outbox_events / inbox_messages

Giống Reservation Service.

## 9. Message Contracts

### ReserveInventoryCommand

Sent by Reservation Service.

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ],
  "occurredAt": "2026-05-30T10:00:00Z"
}
```

### InventoryReservedEvent

Sent by Inventory Service.

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "holdId": "uuid",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ],
  "occurredAt": "2026-05-30T10:00:01Z"
}
```

### InventoryReservationRejectedEvent

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "reason": "INSUFFICIENT_STOCK",
  "unavailableItems": [
    { "sku": "B200", "requested": 3, "available": 1 }
  ],
  "occurredAt": "2026-05-30T10:00:01Z"
}
```

### ConfirmInventoryCommand

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "occurredAt": "2026-05-30T10:01:00Z"
}
```

### InventoryConfirmedEvent

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "occurredAt": "2026-05-30T10:01:01Z"
}
```

### ReleaseInventoryCommand

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "reason": "CANCELLED_BY_CLIENT",
  "occurredAt": "2026-05-30T10:02:00Z"
}
```

### InventoryReleasedEvent

```json
{
  "messageId": "uuid",
  "reservationId": "uuid",
  "orderId": "ORD-1001",
  "occurredAt": "2026-05-30T10:02:01Z"
}
```

## 10. Main Flows

### 10.1 Create Reservation - Success

```text
1. Client -> Reservation Service: POST /api/v1/reservations
2. Reservation Service:
   - validate request
   - create reservation status = RESERVING
   - create reservation_items
   - save ReserveInventoryCommand into outbox in same DB transaction
3. Outbox publisher publishes ReserveInventoryCommand
4. Inventory Service consumes command
5. Inventory Service:
   - check inbox for messageId
   - reserve all requested SKUs in one local DB transaction
   - create inventory_hold status = HELD
   - reduce available_stock
   - increase reserved_stock
   - save InventoryReservedEvent into outbox
6. Reservation Service consumes InventoryReservedEvent
7. Reservation Service:
   - move reservation RESERVING -> PENDING
```

API có thể trả:

- `202 Accepted` nếu async hoàn toàn.
- `201 Created` nếu Reservation Service đợi kết quả từ Inventory Service trong thời gian ngắn.

Với microservices production-like, nên dùng `202 Accepted`:

```json
{
  "id": "reservation-id",
  "orderId": "ORD-1001",
  "status": "RESERVING"
}
```

Client poll:

```text
GET /api/v1/reservations/{id}
```

### 10.2 Create Reservation - Insufficient Stock

```text
1. Client tạo reservation
2. Reservation Service tạo status = RESERVING và publish ReserveInventoryCommand
3. Inventory Service kiểm tra stock
4. Nếu bất kỳ SKU nào thiếu:
   - không trừ bất kỳ SKU nào
   - không tạo HELD hold
   - publish InventoryReservationRejectedEvent
5. Reservation Service nhận event
6. Reservation Service move RESERVING -> REJECTED
```

Điểm quan trọng: Inventory Service phải check và reserve tất cả items trong cùng một local transaction. Nếu thiếu một SKU, reject toàn bộ command.

### 10.3 Confirm Reservation

```text
1. Client -> Reservation Service: POST /api/v1/reservations/{id}/confirm
2. Reservation Service:
   - chỉ cho confirm nếu status = PENDING
   - move PENDING -> CONFIRMING
   - save ConfirmInventoryCommand into outbox
3. Inventory Service consumes command
4. Inventory Service:
   - find inventory_hold by reservation_id
   - chỉ cho HELD -> CONFIRMED
   - decrease reserved_stock
   - decrease on_hand_stock
   - save InventoryConfirmedEvent
5. Reservation Service consumes event
6. Reservation Service move CONFIRMING -> CONFIRMED
```

Nếu command confirm bị retry, Inventory Service thấy hold đã `CONFIRMED` thì trả kết quả idempotent, không trừ stock lần nữa.

### 10.4 Cancel Reservation

```text
1. Client -> Reservation Service: POST /api/v1/reservations/{id}/cancel
2. Reservation Service:
   - chỉ cho cancel nếu status = PENDING
   - move PENDING -> CANCELLING
   - save ReleaseInventoryCommand into outbox
3. Inventory Service consumes command
4. Inventory Service:
   - find inventory_hold by reservation_id
   - chỉ cho HELD -> RELEASED
   - increase available_stock
   - decrease reserved_stock
   - save InventoryReleasedEvent
5. Reservation Service consumes event
6. Reservation Service move CANCELLING -> CANCELLED
```

Nếu reservation đã `CONFIRMED`, Reservation Service reject request cancel ngay, không gửi command sang Inventory.

## 11. Partial Failure Và Revert

### Case A: Hết Hàng Khi Reserve Multi-SKU

Ví dụ request cần:

```text
A100: 5
B200: 3
```

Inventory Service phải xử lý trong một DB transaction:

```text
BEGIN
lock inventory rows for A100, B200
check available_stock của tất cả SKU
nếu SKU nào thiếu -> ROLLBACK và publish rejected event
nếu đủ -> update tất cả rows, create hold, COMMIT
```

Như vậy không có tình huống A100 bị trừ nhưng B200 fail trong cùng Inventory Service.

### Case B: Inventory Reserve Thành Công Nhưng Reservation Service Crash Trước Khi Nhận Event

Không cần revert ngay.

Inventory Service đã lưu `InventoryReservedEvent` trong outbox. Outbox publisher sẽ retry publish. Reservation Service khi sống lại sẽ consume event và move sang `PENDING`.

### Case C: Reservation Service Tạo Reservation Nhưng Crash Trước Khi Publish Command

Không mất command vì `ReserveInventoryCommand` đã được ghi vào outbox cùng transaction tạo reservation.

Outbox publisher đọc lại event chưa publish và gửi tiếp.

### Case D: Reservation Service Confirm/Cancel Nhưng Inventory Service Tạm Thời Down

Reservation Service đã move sang `CONFIRMING` hoặc `CANCELLING` và ghi command vào outbox.

Outbox publisher retry cho đến khi Inventory Service xử lý được.

Client thấy reservation đang processing.

### Case E: Inventory Confirmed Nhưng Reservation Service Chưa Cập Nhật CONFIRMED

Inventory Service đã publish `InventoryConfirmedEvent` qua outbox. Reservation Service sẽ consume sau. Nếu event bị duplicate, inbox/idempotency đảm bảo chỉ process một lần.

### Case F: Cần Revert Khi Reservation Service Fail Sau Khi Inventory Đã HELD

Nếu có business timeout, ví dụ reservation `RESERVING` quá 10 phút chưa sang `PENDING`, có thể có scheduled job:

```text
find stale reservations
mark as REJECTED or EXPIRED
send ReleaseInventoryCommand
```

Inventory Service release hold nếu status còn `HELD`.

## 12. Concurrency Safety

Inventory Service là nơi duy nhất cần đảm bảo không oversell.

Có 2 cách chính:

### Option 1: Atomic Conditional Update

Với mỗi SKU:

```sql
UPDATE inventory
SET available_stock = available_stock - :quantity,
    reserved_stock = reserved_stock + :quantity,
    version = version + 1,
    updated_at = now()
WHERE sku = :sku
  AND available_stock >= :quantity;
```

Nếu affected row = 0, SKU không đủ stock.

Nhưng với multi-SKU, cần transaction. Nếu một SKU fail, rollback tất cả update trước đó.

### Option 2: Pessimistic Locking

```sql
SELECT *
FROM inventory
WHERE sku IN (:skus)
ORDER BY sku
FOR UPDATE;
```

Sau đó check tất cả available stock trong application, rồi update.

Nên `ORDER BY sku` trước khi lock để giảm nguy cơ deadlock khi nhiều request cùng lock nhiều SKU theo thứ tự khác nhau.

Khuyến nghị cho bài này: dùng pessimistic locking cho flow reserve multi-SKU vì code dễ đọc và thể hiện rõ concurrency intent.

## 13. Idempotency Rules

### Public API Idempotency

`POST /api/v1/reservations` nên idempotent theo:

- `orderId`, hoặc
- header `Idempotency-Key`

Với requirement có `orderId`, có thể unique `order_id`.

Nếu client retry cùng `orderId`, Reservation Service trả lại reservation đã tồn tại thay vì tạo mới.

### Message Idempotency

Mỗi message có `messageId`.

Mỗi consumer ghi vào `inbox_messages`.

Pseudo flow:

```text
BEGIN
if message_id exists in inbox_messages:
  COMMIT and ignore
process business logic
insert message_id into inbox_messages
COMMIT
```

### Command Idempotency In Inventory

Ngoài `messageId`, Inventory Service cần idempotent theo `reservation_id`.

Ví dụ ReserveInventoryCommand được gửi lại với messageId khác nhưng cùng reservationId:

- Nếu hold đã `HELD`: publish InventoryReservedEvent lại.
- Nếu hold đã `CONFIRMED`: coi như đã reserve trước đó, không trừ stock nữa.
- Nếu hold đã `RELEASED`: tùy policy, reject hoặc publish current state event.

## 14. API Design

### Reservation Service API

#### Create Reservation

```http
POST /api/v1/reservations
Content-Type: application/json
Idempotency-Key: optional-key
```

Request:

```json
{
  "orderId": "ORD-1001",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ]
}
```

Response:

```http
202 Accepted
```

```json
{
  "id": "uuid",
  "orderId": "ORD-1001",
  "status": "RESERVING",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ]
}
```

#### Get Reservation

```http
GET /api/v1/reservations/{id}
```

Response:

```json
{
  "id": "uuid",
  "orderId": "ORD-1001",
  "status": "PENDING",
  "failureReason": null,
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ],
  "createdAt": "2026-05-30T10:00:00Z",
  "updatedAt": "2026-05-30T10:00:01Z"
}
```

#### Confirm Reservation

```http
POST /api/v1/reservations/{id}/confirm
```

Possible responses:

- `202 Accepted`: moved to `CONFIRMING`
- `409 Conflict`: reservation is not `PENDING`
- `404 Not Found`: reservation not found

#### Cancel Reservation

```http
POST /api/v1/reservations/{id}/cancel
```

Possible responses:

- `202 Accepted`: moved to `CANCELLING`
- `409 Conflict`: reservation is not `PENDING`, especially already `CONFIRMED`
- `404 Not Found`: reservation not found

### Inventory Service API

#### Get Stock

```http
GET /api/v1/inventory/{sku}
```

Response:

```json
{
  "sku": "A100",
  "onHandStock": 100,
  "availableStock": 70,
  "reservedStock": 30,
  "updatedAt": "2026-05-30T10:00:01Z"
}
```

Reserve/confirm/release inventory nên là internal command qua message broker. Nếu take-home cần demo dễ hơn, có thể expose internal endpoints dưới `/internal/api/v1/inventory/*`, nhưng public API nên đi qua Reservation Service.

## 15. Error Handling

Dùng error response thống nhất:

```json
{
  "code": "RESERVATION_NOT_PENDING",
  "message": "Only PENDING reservations can be confirmed.",
  "details": {
    "reservationId": "uuid",
    "currentStatus": "CONFIRMED"
  },
  "traceId": "..."
}
```

Common codes:

- `VALIDATION_ERROR`
- `RESERVATION_NOT_FOUND`
- `RESERVATION_NOT_PENDING`
- `RESERVATION_ALREADY_EXISTS`
- `INSUFFICIENT_STOCK`
- `SKU_NOT_FOUND`
- `MESSAGE_PROCESSING_FAILED`

## 16. Design Patterns

Requirement yêu cầu ít nhất:

### State Pattern

Áp dụng trong Reservation Service:

- Mỗi reservation status có transition hợp lệ.
- Không cho `CONFIRMED -> CANCELLED`.
- Không cho `REJECTED -> CONFIRMED`.

Có thể implement:

```text
ReservationState
  - RequestedState
  - ReservingState
  - PendingState
  - ConfirmingState
  - CancellingState
  - ConfirmedState
  - CancelledState
  - RejectedState
```

Hoặc đơn giản hơn:

```text
ReservationStateMachine
```

với transition table.

### Factory Pattern

Áp dụng khi tạo reservation:

```text
ReservationFactory.create(orderId, items)
```

Factory đảm bảo:

- orderId hợp lệ
- item không empty
- quantity > 0
- merge duplicate SKU nếu cần
- default status = RESERVING
- tạo domain events/commands cần thiết

### Saga Pattern

Áp dụng trong Reservation Service như orchestrator.

Reservation Service quyết định bước tiếp theo:

- create -> reserve inventory
- pending -> confirm inventory
- pending -> release inventory
- failed/timeout -> compensate release

### Outbox Pattern

Dùng để đảm bảo local DB commit và event publication không bị tách rời.

### Inbox Pattern

Dùng để đảm bảo consumer idempotent khi message duplicate.

## 17. Suggested Java Project Structure

### Root

```text
warehouse-inventory-reservation-system/
  docker-compose.yml
  reservation-service/
  inventory-service/
  README.md
  microservices-saga-design.md
```

### Reservation Service

```text
reservation-service/
  src/main/java/com/example/reservation/
    ReservationServiceApplication.java
    api/
      ReservationController.java
      dto/
      error/
    application/
      command/
      handler/
      saga/
      port/
    domain/
      model/
      state/
      factory/
      event/
      exception/
    infrastructure/
      persistence/
      messaging/
      outbox/
      inbox/
      config/
  src/main/resources/
    db/migration/
    application.yml
```

### Inventory Service

```text
inventory-service/
  src/main/java/com/example/inventory/
    InventoryServiceApplication.java
    api/
      InventoryController.java
      dto/
      error/
    application/
      command/
      handler/
      port/
    domain/
      model/
      service/
      exception/
    infrastructure/
      persistence/
      messaging/
      outbox/
      inbox/
      config/
  src/main/resources/
    db/migration/
    application.yml
```

## 18. Testing Strategy

### Unit Tests

Reservation Service:

- Factory rejects invalid request.
- State machine only allows valid transitions.
- Confirm rejected if status is not `PENDING`.
- Cancel rejected if status is `CONFIRMED`.
- Event handler moves `RESERVING -> PENDING` on `InventoryReservedEvent`.
- Event handler moves `RESERVING -> REJECTED` on `InventoryReservationRejectedEvent`.

Inventory Service:

- Reserve succeeds when all SKUs available.
- Reserve rejects when any SKU unavailable.
- Confirm changes `HELD -> CONFIRMED`.
- Release changes `HELD -> RELEASED`.
- Duplicate command does not double deduct stock.

### Integration Tests

Use Testcontainers PostgreSQL.

Must-have tests:

- Concurrent reserve same SKU, stock insufficient for all requests: no oversell.
- Multi-SKU request fails atomically when one SKU unavailable.
- Duplicate `ReserveInventoryCommand` does not double deduct.
- Duplicate `InventoryReservedEvent` does not move reservation incorrectly.
- Cancel returns available stock.
- Confirm consumes reserved stock.

### End-To-End Tests

With docker-compose:

```text
PostgreSQL reservation DB
PostgreSQL inventory DB
Kafka/RabbitMQ
Reservation Service
Inventory Service
```

Flows:

- Create reservation -> eventually PENDING.
- Create reservation with insufficient stock -> eventually REJECTED.
- Confirm PENDING reservation -> eventually CONFIRMED.
- Cancel PENDING reservation -> eventually CANCELLED.

## 19. Trade-Offs

### Pros

- Scale Inventory Service độc lập khi traffic stock check/reserve cao.
- Reservation lifecycle tách riêng, code dễ mở rộng.
- Không oversell vì Inventory Service là single owner của stock.
- Recover được khi service crash/message duplicate.
- Gần với cách hệ thống lớn implement thực tế.

### Cons

- Phức tạp hơn monolith rất nhiều.
- API create reservation là eventually consistent.
- Cần message broker, outbox publisher, inbox consumer.
- Cần observability tốt: logs, metrics, tracing.
- Test khó hơn, nhất là concurrency và retry.

## 20. Implementation Recommendation

Nếu implement trong repo này, nên đi theo thứ tự:

1. Tạo multi-module Maven/Gradle project.
2. Build Reservation Service với DB, migration, REST API, state machine, factory.
3. Build Inventory Service với DB, migration, stock locking, hold lifecycle.
4. Thêm shared message contracts.
5. Thêm Outbox/Inbox.
6. Kết nối broker.
7. Viết concurrency tests cho Inventory trước.
8. Viết integration tests cho saga.
9. Viết README giải thích design patterns và cách run.

Thứ tự quan trọng nhất:

```text
Inventory correctness first
Reservation lifecycle second
Messaging reliability third
End-to-end polish last
```

Nếu Inventory không correct dưới concurrent load, cả hệ thống microservices sẽ không có ý nghĩa.
