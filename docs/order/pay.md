# POST /api/v1/orders/{orderId}/pay — Pay for Order

## Overview

Advances an order from `CONFIRMED` to `PAID`. The domain model enforces the required predecessor
status. `paymentDetails` is accepted in the request body and passed through to the use case,
but the current domain model stores no payment data — the field is reserved for future integration.

Returns **204 No Content**.

---

## Request

| Part | Detail |
|------|--------|
| Method | `POST` |
| Path | `/api/v1/orders/{orderId}/pay` |
| Path param | `orderId` — UUID of the order to pay |
| Content-Type | `application/json` |

**Body — `PayOrderRequest`:**

```json
{
  "paymentDetails": "card_token_abc123"
}
```

| Field | Type | Constraint |
|-------|------|-----------|
| `paymentDetails` | String | `@NotBlank` |

---

## Detailed Flow

### 1. HTTP layer — `OrderController.pay()`

- `@Valid` validates `PayOrderRequest`. If `paymentDetails` is blank, `MethodArgumentNotValidException` is thrown before the controller body runs.
- Delegates to the use case:

```kotlin
orderUseCase.pay(orderId, request.paymentDetails)
```

### 2. Application layer — `OrderService.pay()` (`@Transactional`)

#### 2a. Load order

Identical to the confirm flow: `findOrThrow(orderId)` calls `OrderRepositoryAdapter.findById()`,
which runs two queries (`SELECT orders` + `SELECT order_items`) and assembles the domain `Order`
via `OrderMapper.toDomain()`. Throws `OrderNotFoundException` if not found.

#### 2b. Domain transition — `Order.pay()`

```kotlin
orderRepository.save(order.pay())
```

`Order.pay()` calls `transition(OrderStatus.PAID, OrderStatus.CONFIRMED)`:

- If `status == CONFIRMED` → returns new immutable `Order` with `status = PAID`, `updatedAt = now`.
- Otherwise → throws `InvalidOrderTransitionException`.

> **Note:** `paymentDetails` is received by the service but is not stored anywhere in the current implementation. The domain `Order` model has no payment field. The parameter is a placeholder for a future payment-gateway integration.

#### 2c. Persist

`OrderRepositoryAdapter.save()` → `UPDATE orders SET status = 'PAID', updated_at = ? WHERE id = ?`

Spring commits.

### 3. Response

**HTTP 204 No Content**.

---

## Order State Machine

```
NEW ──confirm()──► CONFIRMED ──pay()──► PAID ──ship()──► SHIPPED
                       │                 │
                  cancel()           cancel()
                       │                 │
                       ▼                 ▼
                   CANCELLED         CANCELLED
```

This endpoint is only valid from `CONFIRMED`.

---

## Error Handling

| Scenario | Exception | Handler | HTTP Response |
|----------|-----------|---------|---------------|
| `paymentDetails` is blank | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation()` | `400` `{"error": "paymentDetails: must not be blank", "code": "VALIDATION_ERROR"}` |
| Order does not exist | `OrderNotFoundException` | `GlobalExceptionHandler.handleOrderNotFound()` | `404` `{"error": "Order not found: …", "code": "ORDER_NOT_FOUND"}` |
| Order is not in `CONFIRMED` status | `InvalidOrderTransitionException` | `GlobalExceptionHandler.handleInvalidTransition()` | `400` `{"error": "Invalid order status transition: X -> PAID", "code": "INVALID_ORDER_TRANSITION"}` |
| DB unreachable | `DataAccessException` | Not explicitly handled | `500 Internal Server Error` |

---

## PlantUML Sequence Diagram

```plantuml
@startuml pay

title POST /api/v1/orders/{orderId}/pay

actor Client
participant "OrderController\n(REST adapter)" as Controller
participant "Bean Validation\n(@Valid)" as BV
participant "OrderService\n(Application)" as Service
participant "OrderRepositoryAdapter\n(Persistence adapter)" as Adapter
participant "OrderJpaRepository" as OrderJpa
participant "OrderItemJpaRepository" as ItemJpa
participant "Order\n(Domain)" as Domain
database "PostgreSQL" as DB

Client -> Controller : POST /api/v1/orders/{orderId}/pay\n{ paymentDetails }
activate Controller

Controller -> BV : validate PayOrderRequest
activate BV
alt paymentDetails is blank
    BV --> Controller : MethodArgumentNotValidException
    Controller --> Client : 400 { error, code: VALIDATION_ERROR }
end
BV --> Controller : valid
deactivate BV

Controller -> Service : pay(orderId, paymentDetails)
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
Adapter --> Service : Order (status=CONFIRMED)
deactivate Adapter

note over Service : paymentDetails received but not stored\n(domain model has no payment field)

Service -> Domain : order.pay()
activate Domain
alt order.status != CONFIRMED
    Domain --> Service : throw InvalidOrderTransitionException
    Controller --> Client : 400 { error, code: INVALID_ORDER_TRANSITION }
end
Domain --> Service : new Order (status=PAID, updatedAt=now)
deactivate Domain

Service -> Adapter : save(paidOrder)
activate Adapter
Adapter -> OrderJpa : save(OrderJpaEntity)
OrderJpa -> DB : UPDATE orders SET status='PAID', updated_at=? WHERE id=?
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
