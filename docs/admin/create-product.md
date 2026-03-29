# POST /admin/products — Create Product

## Overview

Seeds a new product and its initial inventory record. Only available when the Spring profile is
**not** `prod` (guarded by `@Profile("!prod")` on `AdminController`). Intended for development,
testing, and data seeding — not for production use.

Returns **201 Created** with the new product's ID and name.

---

## Request

| Part | Detail |
|------|--------|
| Method | `POST` |
| Path | `/admin/products` |
| Content-Type | `application/json` |

**Body — `CreateProductRequest`:**

```json
{
  "name": "Widget Pro",
  "initialStock": 100
}
```

| Field | Type | Constraint |
|-------|------|-----------|
| `name` | String | `@NotBlank` |
| `initialStock` | Int | `@Min(0)` |

---

## Response — `CreateProductResponse`

```json
{
  "id": "uuid",
  "name": "Widget Pro"
}
```

---

## Detailed Flow

### 1. HTTP layer — `AdminController.createProduct()`

- `@Valid` validates `CreateProductRequest`. Blank name or negative stock raises `MethodArgumentNotValidException`.
- Delegates to the use case:

```kotlin
val product = adminUseCase.createProduct(request.name, request.initialStock)
return ResponseEntity.status(HttpStatus.CREATED).body(CreateProductResponse(product.id, product.name))
```

### 2. Application layer — `AdminService.createProduct()` (`@Transactional`)

#### 2a. Create and persist the product

```kotlin
val product = Product(id = UUID.randomUUID(), name = name, createdAt = Instant.now())
val saved = productRepository.save(product)
```

`ProductRepositoryAdapter.save()` → `INSERT INTO products (id, name, created_at) VALUES (…)`

#### 2b. Create and persist the inventory record

```kotlin
inventoryRepository.save(
    Inventory(
        productId = saved.id,
        availableQuantity = initialStock,
        reservedQuantity = 0,
        totalQuantity = initialStock
    )
)
```

`InventoryRepositoryAdapter.save()` → `INSERT INTO inventory (product_id, available_quantity, reserved_quantity, total_quantity) VALUES (…)`

Both inserts participate in the same transaction. If the second insert fails (e.g. constraint violation),
the first is rolled back.

Spring commits.

### 3. Response

Controller returns **201 Created** with `CreateProductResponse(id, name)`.

---

## Profile Guard

`AdminController` is annotated `@Profile("!prod")`. Spring does not register this bean when the
`prod` profile is active — the `/admin` endpoints return 404 in production environments.

---

## Error Handling

| Scenario | Exception | Handler | HTTP Response |
|----------|-----------|---------|---------------|
| `name` is blank | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation()` | `400` `{"error": "name: must not be blank", "code": "VALIDATION_ERROR"}` |
| `initialStock` < 0 | `MethodArgumentNotValidException` | same | `400` VALIDATION_ERROR |
| Endpoint hit with `prod` profile | *(bean not registered)* | Spring — no handler found | `404 Not Found` |
| DB unreachable | `DataAccessException` | Not explicitly handled | `500 Internal Server Error` |

---

## PlantUML Sequence Diagram

```plantuml
@startuml create-product

title POST /admin/products

actor Client
participant "AdminController\n(@Profile(\"!prod\"))" as Controller
participant "Bean Validation\n(@Valid)" as BV
participant "AdminService\n(Application)" as Service
participant "ProductRepositoryAdapter" as ProdAdapter
participant "InventoryRepositoryAdapter" as InvAdapter
database "PostgreSQL" as DB

note over Controller : Bean only exists when\nSpring profile != prod

Client -> Controller : POST /admin/products\n{ name, initialStock }
activate Controller

Controller -> BV : validate CreateProductRequest
activate BV
alt name is blank OR initialStock < 0
    BV --> Controller : MethodArgumentNotValidException
    Controller --> Client : 400 { error, code: VALIDATION_ERROR }
end
BV --> Controller : valid
deactivate BV

Controller -> Service : createProduct(name, initialStock)
activate Service
note over Service : @Transactional — DB transaction opens

Service -> Service : Product(id=UUID.random(), name, createdAt=now)

Service -> ProdAdapter : save(product)
activate ProdAdapter
ProdAdapter -> DB : INSERT INTO products (id, name, created_at) VALUES (…)
DB --> ProdAdapter : saved
ProdAdapter --> Service : Product
deactivate ProdAdapter

Service -> InvAdapter : save(Inventory(productId, available=initialStock, reserved=0, total=initialStock))
activate InvAdapter
InvAdapter -> DB : INSERT INTO inventory\n(product_id, available_quantity, reserved_quantity, total_quantity)\nVALUES (…)
DB --> InvAdapter : saved
InvAdapter --> Service : Inventory
deactivate InvAdapter

note over Service : @Transactional — commits
Service --> Controller : Product
deactivate Service

Controller --> Client : 201 Created\n{ id, name }
deactivate Controller

@enduml
```
