# Senior Software Engineer — Take-Home Assignment
## Goal

Choose **ONE** of the two challenges below and build a working service.

Your solution should demonstrate:
- Clean architecture and SOLID principles
- At least 2 design patterns (name them in your README)
- A working REST API with proper error handling
- Database modelling with migrations
- Unit tests
- Concurrency safety — your solution must be correct when multiple requests arrive simultaneously
- A clear README


## Warehouse Inventory Reservation System

### The Scenario

Multiple clients try to reserve warehouse inventory at the same time for different orders. Your service must ensure no item is oversold, reservations are tracked, and stock stays consistent under concurrent load.

### Functional Requirements

**Reserve inventory**
```
POST /api/v1/reservations
{
  "orderId": "ORD-1001",
  "items": [
    { "sku": "A100", "quantity": 5 },
    { "sku": "B200", "quantity": 3 }
  ]
}
```
- Check available stock for each SKU
- If all items are available, create the reservation and reduce available stock
- If any item is unavailable, reject the entire reservation with a clear error
- Two simultaneous requests for the same SKU must not both succeed if stock is insufficient

**Confirm a reservation**
```
POST /api/v1/reservations/{id}/confirm
```
- Moves reservation from `PENDING` to `CONFIRMED`
- Only a `PENDING` reservation can be confirmed

**Cancel a reservation**
```
POST /api/v1/reservations/{id}/cancel
```
- Moves reservation from `PENDING` to `CANCELLED`
- Returns the reserved quantity back to available stock
- A `CONFIRMED` reservation cannot be cancelled — return a clear error

**Get a reservation**
```
GET /api/v1/reservations/{id}
```

**Get current stock for a SKU**
```
GET /api/v1/inventory/{sku}
```

### Reservation Lifecycle

```
PENDING  →  CONFIRMED
PENDING  →  CANCELLED
```

`CONFIRMED` is a terminal state — no further changes allowed.

### Example Stock Scenario

```
SKU A100: total stock = 100, available = 100

Request 1: reserve 30  →  success,  available = 70
Request 2: reserve 40  →  success,  available = 30
Request 3: reserve 50  →  REJECTED — only 30 available
```

### Database Tables

Design and create these tables (you decide the exact columns and types):

- `products` — SKU, name, description
- `inventory` — SKU, total stock, available stock, reserved stock
- `reservations` — id, order ID, status, created at
- `reservation_items` — reservation id, SKU, quantity

### Design Patterns to Apply

Name these in your README and show where they appear in the code:
- **State Pattern** — reservation lifecycle transitions
- **Factory Pattern** — reservation creation logic