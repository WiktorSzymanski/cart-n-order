# Cart & Order Service

A Spring Boot (Kotlin) REST API implementing a Cart → Order → Inventory workflow, built with hexagonal (ports & adapters) architecture.

## Tech Stack

| Layer | Technology |
|---|---|
| Runtime | Java 24, Kotlin 2.2.21 |
| Framework | Spring Boot 4.0.4 |
| Persistence | Spring Data JPA, Hibernate 7, PostgreSQL 16 |
| Migrations | Flyway 11 |
| API Docs | SpringDoc OpenAPI (Swagger UI) |
| Build | Gradle 9.4 |
| Tests | JUnit 5, Testcontainers 2 |

## Running Locally

**Prerequisites:** Docker, JDK 24

```bash
# Start only the database
docker compose up -d postgres

# Run the application
./gradlew bootRun
```

The API is available at `http://localhost:8080`.
Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/api-docs`

**Full stack (app + database) in Docker:**

```bash
./gradlew build
docker compose up
```

## Running Tests

Tests use Testcontainers and require a Docker daemon. No external database needed.

```bash
./gradlew test
```

## API Reference

### Cart

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/carts/{customerId}/items` | Add or update item in cart |
| `DELETE` | `/api/v1/carts/{customerId}/items/{productId}` | Remove item (optionally partial quantity) |
| `GET` | `/api/v1/carts/{customerId}` | Get cart contents |

**Add item:**
```json
POST /api/v1/carts/{customerId}/items
{ "productId": "uuid", "quantity": 2, "unitPrice": 999.99 }
→ 204
```

**Remove item (partial):**
```json
DELETE /api/v1/carts/{customerId}/items/{productId}
{ "quantity": 1 }
→ 204
```

**Get cart:**
```json
GET /api/v1/carts/{customerId}
→ { "items": [...], "totalItems": 2, "totalAmount": 1999.98 }
```

### Orders

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/orders/from-cart/{customerId}` | Create order from cart, reserve stock |
| `POST` | `/api/v1/orders/{orderId}/confirm` | Advance to CONFIRMED |
| `POST` | `/api/v1/orders/{orderId}/pay` | Advance to PAID |
| `POST` | `/api/v1/orders/{orderId}/ship` | Advance to SHIPPED |
| `DELETE` | `/api/v1/orders/{orderId}/cancel` | Cancel and release reserved stock |
| `GET` | `/api/v1/orders/{orderId}` | Get order details |
| `GET` | `/api/v1/orders/summary` | Aggregate totals by date range |
| `GET` | `/api/v1/customers/{customerId}/orders` | List customer orders |

**Create order:**
```
POST /api/v1/orders/from-cart/{customerId}
→ 201 { "orderId": "uuid" }
→ 409 { "error": "...", "code": "INSUFFICIENT_STOCK" }
```

**Pay:**
```json
POST /api/v1/orders/{orderId}/pay
{ "paymentDetails": "ref-123" }
→ 204
```

**Ship:**
```json
POST /api/v1/orders/{orderId}/ship
{ "trackingNumber": "ABC123" }
→ 204
```

**Cancel:**
```json
DELETE /api/v1/orders/{orderId}/cancel
{ "reason": "customer-request" }
→ 204
```

**Summary:**
```
GET /api/v1/orders/summary?from=2026-01-01&to=2026-03-25&status=PAID
→ { "totalOrders": 1234, "totalVolume": 12500.50 }
```

**Customer orders:**
```
GET /api/v1/customers/{customerId}/orders?status=CONFIRMED,PAID&limit=20&offset=0
→ { "orders": [...], "total": 42 }
```

### Products

```
GET /api/v1/products/{productId}/stock
→ { "available": 95, "reserved": 5 }
```

### Admin (disabled in `prod` profile)

```json
POST /admin/products
{ "name": "Laptop", "initialStock": 100 }
→ 201 { "id": "uuid", "name": "Laptop" }

POST /admin/customers
{ "count": 1000 }
→ 201 { "customerIds": ["uuid", ...] }
```

## Order Status Machine

```
NEW → CONFIRMED → PAID → SHIPPED
 ↓        ↓        ↓
           CANCELLED (not allowed from SHIPPED)
```

Cancelling releases all reserved inventory back to available.

## Error Responses

All errors follow the same shape:

```json
{ "error": "human-readable message", "code": "MACHINE_CODE" }
```

| Code | HTTP | Cause |
|---|---|---|
| `INSUFFICIENT_STOCK` | 409 | Not enough available inventory when creating order |
| `ORDER_NOT_FOUND` | 404 | Order ID does not exist |
| `CART_EMPTY` | 400 | Attempting to create order from empty/missing cart |
| `INVALID_ORDER_TRANSITION` | 400 | Status change not permitted (e.g. SHIPPED → CONFIRMED) |
| `PRODUCT_NOT_FOUND` | 404 | Product ID does not exist |
| `VALIDATION_ERROR` | 400 | Request body failed validation |

## Architecture

The project follows hexagonal (ports & adapters) architecture:

```
infrastructure/adapter/inbound/rest   ← HTTP (controllers, DTOs)
        ↓ calls
application/service                   ← use cases (@Service @Transactional)
        ↓ depends on
domain/port/outbound                  ← repository interfaces
        ↑ implemented by
infrastructure/adapter/outbound/persistence  ← JPA (entities, repositories, adapters)
```

The `domain/` package has zero framework dependencies — only plain Kotlin and the Java standard library.

## Concurrency

Stock reservation uses a single `UPDATE … WHERE available_quantity >= qty` per item. If the affected-row count is 0 (another request already took the stock), `InsufficientStockException` is thrown and the entire `createFromCart` transaction rolls back. No application-level locks or retries are used; conflicts surface as HTTP 409.

## Configuration

Key properties in `src/main/resources/application.yaml`:

| Property | Default | Notes |
|---|---|---|
| `spring.datasource.url` | `jdbc:postgresql://localhost:5432/cart_n_order` | Override with `SPRING_DATASOURCE_URL` env var |
| `spring.datasource.hikari.maximum-pool-size` | 20 | |
| `spring.jpa.hibernate.ddl-auto` | `validate` | Schema must exist (created by Flyway) |
