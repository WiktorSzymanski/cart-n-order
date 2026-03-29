# DELETE /api/v1/carts/{customerId}/items/{productId} — Remove Item from Cart

## Overview

Removes a product from the customer's cart. Behaviour depends on whether `quantity` is provided in the optional request body:

- **No body / `quantity` omitted** — the entire `CartItem` is removed regardless of how many units are in the cart.
- **`quantity` provided and `quantity >= existing.quantity`** — the entire `CartItem` is removed.
- **`quantity` provided and `quantity < existing.quantity`** — the item's quantity is decremented.

If removing the item leaves the cart empty, **the cart row itself is deleted** from the database.
Always returns **204 No Content**.

---

## Request

| Part | Detail |
|------|--------|
| Method | `DELETE` |
| Path | `/api/v1/carts/{customerId}/items/{productId}` |
| Path params | `customerId` — UUID of the customer; `productId` — UUID of the product to remove |
| Content-Type | `application/json` (optional) |

**Body — `RemoveItemRequest` (optional):**

```json
{
  "quantity": 1
}
```

| Field | Type | Constraint |
|-------|------|-----------|
| `quantity` | Int? | `@Min(1)` (only validated when provided) |

Omitting the body entirely is valid and means "remove all units".

---

## Detailed Flow

### 1. HTTP layer — `CartController.removeItem()`

- Spring attempts to deserialize the body into `RemoveItemRequest?`. Because the parameter is annotated `@RequestBody(required = false)`, a missing body resolves to `null` without error.
- `@Valid` runs only when the body is present. If `quantity` is provided but less than 1, `MethodArgumentNotValidException` is thrown.
- The controller delegates to the inbound port:

```kotlin
cartUseCase.removeItem(customerId, productId, request?.quantity)
```

### 2. Application layer — `CartService.removeItem()` (`@Transactional`)

A Spring transaction is opened.

#### 2a. Load or create cart

```kotlin
val cart = cartRepository.findByCustomerId(customerId)
    ?: cartMapper.emptyCart(customerId)
```

Identical to the add-item flow: if no cart row exists, an in-memory empty `Cart` is returned. Operating on an empty cart is harmless — domain logic simply returns the same empty cart.

#### 2b. Outbound adapter — `CartRepositoryAdapter.findByCustomerId()`

Same as add-item: `SELECT * FROM carts WHERE customer_id = ?`, deserializes the JSONB `items` column if a row exists.

#### 2c. Domain logic — `Cart.removeItem()`

```kotlin
val updated = cart.removeItem(productId, quantity)
```

The domain model enforces the removal rules:

1. If `productId` is not in the cart → returns the cart unchanged (no-op, no exception).
2. If `quantity == null` or `quantity >= existing.quantity` → filters out the item entirely.
3. If `quantity < existing.quantity` → copies the item with `quantity - requested quantity`.

Returns a **new immutable `Cart`** with a refreshed `updatedAt`.

#### 2d. Persist or delete

```kotlin
if (updated.items.isEmpty()) {
    cartRepository.deleteByCustomerId(customerId)
} else {
    cartRepository.save(updated)
}
```

**Branch A — cart is now empty:**

- `CartRepositoryAdapter.deleteByCustomerId()` calls `CartJpaRepository.deleteById(customerId)`.
- Spring Data JPA issues `DELETE FROM carts WHERE customer_id = ?`.

**Branch B — cart still has items:**

- `CartRepositoryAdapter.save()` serializes the updated items list to JSONB and upserts via `CartJpaRepository.save()`.

Spring commits the transaction.

### 3. Response

The controller returns `ResponseEntity.noContent().build()` → **HTTP 204 No Content**.

---

## Error Handling

| Scenario | Exception | Handler | HTTP Response |
|----------|-----------|---------|---------------|
| `quantity` provided but < 1 | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation()` | `400` `{"error": "quantity: must be greater than or equal to 1", "code": "VALIDATION_ERROR"}` |
| Product not in cart | *(no exception)* | — | `204` (silent no-op) |
| Customer has no cart | *(no exception)* | — | `204` (silent no-op — empty cart is created in memory and immediately discarded) |
| DB unreachable | `DataAccessException` (unchecked) | Not explicitly handled | `500 Internal Server Error` |

> **Note:** `CartNotFoundException` is defined in the domain but is **never thrown** in this flow.

---

## PlantUML Sequence Diagram

```plantuml
@startuml remove-item

title DELETE /api/v1/carts/{customerId}/items/{productId}

actor Client
participant "CartController\n(REST adapter)" as Controller
participant "Bean Validation\n(@Valid)" as BV
participant "CartService\n(Application)" as Service
participant "CartRepositoryAdapter\n(Persistence adapter)" as Adapter
participant "CartJpaRepository\n(Spring Data)" as Jpa
participant "CartMapper" as Mapper
participant "Cart\n(Domain)" as Domain
database "PostgreSQL\ncarts table" as DB

Client -> Controller : DELETE /api/v1/carts/{customerId}/items/{productId}\n[body: { quantity } optional]

activate Controller

alt Body present
    Controller -> BV : validate RemoveItemRequest
    activate BV
    alt quantity < 1
        BV --> Controller : MethodArgumentNotValidException
        Controller --> Client : 400 { error, code: VALIDATION_ERROR }
    end
    BV --> Controller : valid
    deactivate BV
end

Controller -> Service : removeItem(customerId, productId, quantity?)
activate Service
note over Service : @Transactional — DB transaction opens

Service -> Adapter : findByCustomerId(customerId)
activate Adapter
Adapter -> Jpa : findById(customerId)
activate Jpa
Jpa -> DB : SELECT * FROM carts WHERE customer_id = ?
DB --> Jpa : CartJpaEntity or empty
Jpa --> Adapter : Optional<CartJpaEntity>
deactivate Jpa

alt Cart exists
    Adapter -> Mapper : toDomain(CartJpaEntity)
    activate Mapper
    note right of Mapper : Deserializes JSONB items\nvia Jackson ObjectMapper
    Mapper --> Adapter : Cart
    deactivate Mapper
    Adapter --> Service : Cart
else No cart for customer
    Adapter --> Service : null
    Service -> Mapper : emptyCart(customerId)
    activate Mapper
    Mapper --> Service : Cart(items=[])
    deactivate Mapper
end
deactivate Adapter

Service -> Domain : cart.removeItem(productId, quantity?)
activate Domain

alt Product not in cart
    Domain --> Service : Cart unchanged (no-op)
else quantity == null OR quantity >= existing.quantity
    Domain --> Service : new Cart (item removed entirely)
else quantity < existing.quantity
    Domain --> Service : new Cart (quantity decremented)
end
deactivate Domain

alt updated.items is empty
    Service -> Adapter : deleteByCustomerId(customerId)
    activate Adapter
    Adapter -> Jpa : deleteById(customerId)
    activate Jpa
    Jpa -> DB : DELETE FROM carts WHERE customer_id = ?
    DB --> Jpa : ok
    Jpa --> Adapter : void
    deactivate Jpa
    Adapter --> Service : void
    deactivate Adapter
else updated.items not empty
    Service -> Adapter : save(updatedCart)
    activate Adapter
    Adapter -> Mapper : toEntity(cart)
    activate Mapper
    note right of Mapper : Serializes items list\nto JSONB string via Jackson
    Mapper --> Adapter : CartJpaEntity
    deactivate Mapper
    Adapter -> Jpa : save(CartJpaEntity)
    activate Jpa
    Jpa -> DB : INSERT INTO carts … ON CONFLICT UPDATE
    DB --> Jpa : saved entity
    Jpa --> Adapter : CartJpaEntity
    deactivate Jpa
    Adapter --> Service : Cart
    deactivate Adapter
end

note over Service : @Transactional — commits
deactivate Service

Controller --> Client : 204 No Content
deactivate Controller

@enduml
```
