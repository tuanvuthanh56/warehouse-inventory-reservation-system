# Manual Local Happy Case Test

This walkthrough tests the full async happy path:

```text
create reservation -> inventory held -> confirm reservation -> inventory confirmed
```

The commands below are written for Windows PowerShell. Use `curl.exe` instead of `curl` so PowerShell does not call its built-in `Invoke-WebRequest` alias.

## 1. Start the stack

```powershell
docker compose up --build
```

Wait until these containers are running:

- `reservation-service`
- `inventory-service`
- `reservation-db`
- `inventory-db`
- `inventory-rabbitmq`

In another PowerShell terminal, check both services:

```powershell
curl.exe -s http://localhost:8081/actuator/health
curl.exe -s http://localhost:8082/actuator/health
```

Expected result: both return `"status":"UP"`.

## 2. Check initial stock

```powershell
curl.exe -s http://localhost:8082/api/v1/inventory/A100 | ConvertFrom-Json
curl.exe -s http://localhost:8082/api/v1/inventory/B200 | ConvertFrom-Json
```

Expected stock before the test:

```text
A100: onHandStock=100, availableStock=100, reservedStock=0
B200: onHandStock=50,  availableStock=50,  reservedStock=0
```

## 3. Create a reservation

```powershell
$body = @{
  orderId = "ORD-HAPPY-$(Get-Date -Format yyyyMMddHHmmss)"
  items = @(
    @{ sku = "A100"; quantity = 5 },
    @{ sku = "B200"; quantity = 3 }
  )
} | ConvertTo-Json -Depth 5

$reservation = curl.exe -s -X POST http://localhost:8081/api/v1/reservations `
  -H "Content-Type: application/json" `
  -d $body | ConvertFrom-Json

$reservation
$reservationId = $reservation.id
```

Expected result:

- HTTP flow returns a reservation immediately.
- Initial status is usually `RESERVING` because inventory is reserved asynchronously through RabbitMQ.

## 4. Poll until the reservation becomes PENDING

```powershell
do {
  Start-Sleep -Seconds 2
  $reservation = curl.exe -s "http://localhost:8081/api/v1/reservations/$reservationId" | ConvertFrom-Json
  $reservation
} while ($reservation.status -eq "RESERVING")
```

Expected result:

```text
status=PENDING
failureReason is empty/null
items contains A100 quantity 5 and B200 quantity 3
```

If status becomes `REJECTED`, the happy case failed. Check service logs with:

```powershell
docker compose logs reservation-service inventory-service rabbitmq
```

## 5. Verify stock is held

```powershell
curl.exe -s http://localhost:8082/api/v1/inventory/A100 | ConvertFrom-Json
curl.exe -s http://localhost:8082/api/v1/inventory/B200 | ConvertFrom-Json
```

Expected stock after the reservation is `PENDING`:

```text
A100: onHandStock=100, availableStock=95, reservedStock=5
B200: onHandStock=50,  availableStock=47, reservedStock=3
```

## 6. Confirm the reservation

```powershell
$reservation = curl.exe -s -X POST "http://localhost:8081/api/v1/reservations/$reservationId/confirm" | ConvertFrom-Json
$reservation
```

Expected result: status is usually `CONFIRMING`.

Poll until the confirm command is processed:

```powershell
do {
  Start-Sleep -Seconds 2
  $reservation = curl.exe -s "http://localhost:8081/api/v1/reservations/$reservationId" | ConvertFrom-Json
  $reservation
} while ($reservation.status -eq "CONFIRMING")
```

Expected final reservation status:

```text
status=CONFIRMED
```

## 7. Verify final stock

```powershell
curl.exe -s http://localhost:8082/api/v1/inventory/A100 | ConvertFrom-Json
curl.exe -s http://localhost:8082/api/v1/inventory/B200 | ConvertFrom-Json
```

Expected stock after confirmation:

```text
A100: onHandStock=95, availableStock=95, reservedStock=0
B200: onHandStock=47, availableStock=47, reservedStock=0
```

At this point the full happy case is complete.

## Optional cleanup

To stop the stack but keep database data:

```powershell
docker compose down
```

To reset databases and rerun from clean seed data:

```powershell
docker compose down -v
docker compose up --build
```
