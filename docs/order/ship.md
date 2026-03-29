# POST /api/v1/orders/{orderId}/ship — Ship Order

## Overview

Advances an order from `PAID` to `SHIPPED`. This is the **terminal successful state** — once shipped,
an order can no longer be cancelled. The `trackingNumber` is accepted but not stored in the current
domain model; it is a placeholder for a future logistics integration.

Returns **204 No Content**.

---

## Request

| Part | Detail |
|------|--------|
| Method | `POST` |
| Path | `/api/v1/orders/{orderId}/ship` |
| Path param | `orderId` — UUID of the order to ship |
| Content-Type | `application/json` |

**Body — `ShipOrderRequest`:**

```json
{
  "trackingNumber": "DHL-123456789"
}
```

| Field | Type | Constraint |
|-------|------|-----------|
| `trackingNumber` | String | `@NotBlank` |

---

## Detailed Flow

### 1. HTTP layer — `OrderController.ship()`

- `@Valid` validates `ShipOrderRequest`. A blank `trackingNumber` raises `MethodArgumentNotValidException`.
- Delegates to the use case:

```kotlin
orderUseCase.ship(orderId, request.trackingNumber)
```

### 2. Application layer — `OrderService.ship()` (`@Transactional`)

#### 2a. Load order

`findOrThrow(orderId)` → `OrderRepositoryAdapter.findById()` → two SELECT queries (orders + order_items)
→ `OrderMapper.toDomain()`. Throws `OrderNotFoundException` if not found.

#### 2b. Domain transition — `Order.ship()`

```kotlin
orderRepository.save(order.ship())
```

`Order.ship()` calls `transition(OrderStatus.SHIPPED, OrderStatus.PAID)`:

- If `status == PAID` → returns new immutable `Order` with `status = SHIPPED`, `updatedAt = now`.
- Otherwise → throws `InvalidOrderTransitionException`.

> **Note:** `trackingNumber` is passed to the service but the current `Order` domain model carries no tracking field. The parameter is a forward-compatibility hook for a future logistics adapter.

#### 2c. Persist

`OrderRepositoryAdapter.save()` → `UPDATE orders SET status = 'SHIPPED', updated_at = ? WHERE id = ?`

Spring commits.

### 3. Response

**HTTP 204 No Content**.

---

## Order State Machine

```
NEW ──confirm()──► CONFIRMED ──pay()──► PAID ──ship()──► SHIPPED
                                                           (terminal — cannot cancel)
```

This endpoint is only valid from `PAID`. After shipping, `cancel()` will throw
`InvalidOrderTransitionException` because `SHIPPED` is explicitly excluded from cancellation.

---

## Error Handling

| Scenario | Exception | Handler | HTTP Response |
|----------|-----------|---------|---------------|
| `trackingNumber` is blank | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation()` | `400` `{"error": "trackingNumber: must not be blank", "code": "VALIDATION_ERROR"}` |
| Order does not exist | `OrderNotFoundException` | `GlobalExceptionHandler.handleOrderNotFound()` | `404` `{"error": "Order not found: …", "code": "ORDER_NOT_FOUND"}` |
| Order is not in `PAID` status | `InvalidOrderTransitionException` | `GlobalExceptionHandler.handleInvalidTransition()` | `400` `{"error": "Invalid order status transition: X -> SHIPPED", "code": "INVALID_ORDER_TRANSITION"}` |
| DB unreachable | `DataAccessException` | Not explicitly handled | `500 Internal Server Error` |

---

## PlantUML Sequence Diagram

```plantuml
@startuml ship

title POST /api/v1/orders/{orderId}/ship

actor Client
participant "OrderController\n(REST adapter)" as Controller
participant "Bean Validation\n(@Valid)" as BV
participant "OrderService\n(Application)" as Service
participant "OrderRepositoryAdapter\n(Persistence adapter)" as Adapter
participant "OrderJpaRepository" as OrderJpa
participant "OrderItemJpaRepository" as ItemJpa
participant "Order\n(Domain)" as Domain
database "PostgreSQL" as DB

Client -> Controller : POST /api/v1/orders/{orderId}/ship\n{ trackingNumber }
activate Controller

Controller -> BV : validate ShipOrderRequest
activate BV
alt trackingNumber is blank
    BV --> Controller : MethodArgumentNotValidException
    Controller --> Client : 400 { error, code: VALIDATION_ERROR }
end
BV --> Controller : valid
deactivate BV

Controller -> Service : ship(orderId, trackingNumber)
activate Service
note over Service : @Transactional — DB transaction opens

Service -> Adapter : findById(orderId)
activate Adapter
Adapter -> OrderJpa : findById(orderId)
OrderJpa -> DB : SELECT * FROM orders WHERE id = ?
DB --> OrderJpa : OrderJpaEntity or empty

alt Order not found
    Adapter --> Service : null
    Service -> Service : throw OrderNotFoundException
    Controller --> Client : 404 { error, code: ORDER_NOT_FOUND }
end

Adapter -> ItemJpa : findAllByOrderId(orderId)
ItemJpa -> DB : SELECT * FROM order_items WHERE order_id = ?
DB --> ItemJpa : List<OrderItemJpaEntity>
Adapter -> Adapter : OrderMapper.toDomain(entity, items)
Adapter --> Service : Order (status=PAID)
deactivate Adapter

note over Service : trackingNumber received but not stored\n(domain model has no tracking field)

Service -> Domain : order.ship()
activate Domain
alt order.status != PAID
    Domain --> Service : throw InvalidOrderTransitionException
    Controller --> Client : 400 { error, code: INVALID_ORDER_TRANSITION }
end
Domain --> Service : new Order (status=SHIPPED, updatedAt=now)
deactivate Domain

Service -> Adapter : save(shippedOrder)
activate Adapter
Adapter -> OrderJpa : save(OrderJpaEntity)
OrderJpa -> DB : UPDATE orders SET status='SHIPPED', updated_at=? WHERE id=?
DB --> OrderJpa : updated
Adapter --> Service : Order
deactivate Adapter

note over Service : @Transactional — commits
Service --> Controller : void
deactivate Service

Controller --> Client : 204 No Content
deactivate Controller

@enduml
```
