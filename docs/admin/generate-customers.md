# POST /admin/customers — Generate Customer IDs

## Overview

Generates a batch of random UUID customer identifiers. No database writes occur — UUIDs are produced
in memory and returned immediately. This endpoint exists purely as a convenience for load-testing and
integration testing: callers receive IDs they can use in subsequent cart and order API calls without
needing a dedicated customer service.

Only available when the Spring profile is **not** `prod`.

Returns **201 Created** with the list of generated UUIDs.

---

## Request

| Part | Detail |
|------|--------|
| Method | `POST` |
| Path | `/admin/customers` |
| Content-Type | `application/json` |

**Body — `GenerateCustomersRequest`:**

```json
{
  "count": 5
}
```

| Field | Type | Constraint |
|-------|------|-----------|
| `count` | Int | `@Min(1)` |

---

## Response — `GenerateCustomersResponse`

```json
{
  "customerIds": [
    "550e8400-e29b-41d4-a716-446655440000",
    "6ba7b810-9dad-11d1-80b4-00c04fd430c8"
  ]
}
```

---

## Detailed Flow

### 1. HTTP layer — `AdminController.generateCustomers()`

- `@Valid` validates `GenerateCustomersRequest`. A `count` less than 1 raises `MethodArgumentNotValidException`.
- Delegates:

```kotlin
val ids = adminUseCase.generateCustomers(request.count)
return ResponseEntity.status(HttpStatus.CREATED).body(GenerateCustomersResponse(ids))
```

### 2. Application layer — `AdminService.generateCustomers()` (`@Transactional`)

```kotlin
override fun generateCustomers(count: Int): List<UUID> =
    (1..count).map { UUID.randomUUID() }
```

Pure in-memory computation — no repository calls. The `@Transactional` annotation is inherited from
the class-level annotation but opens and immediately commits an empty transaction (no-op).

### 3. Response

Controller returns **201 Created** with `GenerateCustomersResponse(customerIds)`.

---

## Profile Guard

Same as `create-product`: `@Profile("!prod")` on `AdminController` means this endpoint does not
exist in production — Spring returns 404 if hit.

---

## Error Handling

| Scenario | Exception | Handler | HTTP Response |
|----------|-----------|---------|---------------|
| `count` < 1 | `MethodArgumentNotValidException` | `GlobalExceptionHandler.handleValidation()` | `400` `{"error": "count: must be greater than or equal to 1", "code": "VALIDATION_ERROR"}` |
| Endpoint hit with `prod` profile | *(bean not registered)* | Spring | `404 Not Found` |

---

## PlantUML Sequence Diagram

```plantuml
@startuml generate-customers

title POST /admin/customers

actor Client
participant "AdminController\n(@Profile(\"!prod\"))" as Controller
participant "Bean Validation\n(@Valid)" as BV
participant "AdminService\n(Application)" as Service

note over Controller : Bean only exists when\nSpring profile != prod

Client -> Controller : POST /admin/customers\n{ count: 5 }
activate Controller

Controller -> BV : validate GenerateCustomersRequest
activate BV
alt count < 1
    BV --> Controller : MethodArgumentNotValidException
    Controller --> Client : 400 { error, code: VALIDATION_ERROR }
end
BV --> Controller : valid
deactivate BV

Controller -> Service : generateCustomers(count)
activate Service
note over Service : @Transactional — empty transaction\n(no DB operations)

Service -> Service : (1..count).map { UUID.randomUUID() }
note right of Service : Pure in-memory computation\nNo database interaction

Service --> Controller : List<UUID>
deactivate Service

Controller --> Client : 201 Created\n{ customerIds: [...] }
deactivate Controller

@enduml
```
