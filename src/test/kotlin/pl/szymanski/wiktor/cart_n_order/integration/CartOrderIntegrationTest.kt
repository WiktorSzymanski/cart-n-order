package pl.szymanski.wiktor.cart_n_order.integration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.server.LocalServerPort
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.http.HttpMethod
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.web.client.RestClient
import org.testcontainers.postgresql.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.AddItemRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CancelOrderRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CartResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateOrderResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateProductRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateProductResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.OrderResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.StockResponse
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.Callable
import java.util.concurrent.Executors

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class CartOrderIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val postgres: PostgreSQLContainer = PostgreSQLContainer("postgres:16")
    }

    @LocalServerPort
    private var port: Int = 0

    private lateinit var client: RestClient

    @BeforeEach
    fun setUp() {
        client = RestClient.builder().baseUrl("http://localhost:$port").build()
    }

    // -------------------------------------------------------------------------
    // Happy path: create product → add to cart → create order → confirm → pay → ship
    // -------------------------------------------------------------------------
    @Test
    fun `happy path full order lifecycle`() {
        val product = createProduct("Laptop", 100)
        val customerId = UUID.randomUUID()

        // Add item to cart
        val addStatus = client.post()
            .uri("/api/v1/carts/$customerId/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(AddItemRequest(product.id, 2, BigDecimal("999.99")))
            .retrieve()
            .toBodilessEntity()
            .statusCode
        assertEquals(HttpStatus.NO_CONTENT, addStatus)

        // Verify cart
        val cart = client.get()
            .uri("/api/v1/carts/$customerId")
            .retrieve()
            .body(CartResponse::class.java)!!
        assertEquals(1, cart.items.size)
        assertEquals(2, cart.totalItems)

        // Create order from cart
        val orderResp = client.post()
            .uri("/api/v1/orders/from-cart/$customerId")
            .retrieve()
            .body(CreateOrderResponse::class.java)!!
        val orderId = orderResp.orderId
        assertNotNull(orderId)

        // Confirm
        val confirmStatus = client.post()
            .uri("/api/v1/orders/$orderId/confirm")
            .retrieve()
            .toBodilessEntity()
            .statusCode
        assertEquals(HttpStatus.NO_CONTENT, confirmStatus)

        // Pay
        val payStatus = client.post()
            .uri("/api/v1/orders/$orderId/pay")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("paymentDetails" to "ref-abc-123"))
            .retrieve()
            .toBodilessEntity()
            .statusCode
        assertEquals(HttpStatus.NO_CONTENT, payStatus)

        // Ship
        val shipStatus = client.post()
            .uri("/api/v1/orders/$orderId/ship")
            .contentType(MediaType.APPLICATION_JSON)
            .body(mapOf("trackingNumber" to "TRACK-001"))
            .retrieve()
            .toBodilessEntity()
            .statusCode
        assertEquals(HttpStatus.NO_CONTENT, shipStatus)

        // Final order status
        val finalOrder = client.get()
            .uri("/api/v1/orders/$orderId")
            .retrieve()
            .body(OrderResponse::class.java)!!
        assertEquals("SHIPPED", finalOrder.status)

        // Stock should be reserved (not released after ship)
        val stock = client.get()
            .uri("/api/v1/products/${product.id}/stock")
            .retrieve()
            .body(StockResponse::class.java)!!
        assertEquals(98, stock.available)
        assertEquals(2, stock.reserved)
    }

    // -------------------------------------------------------------------------
    // Stock conflict: two concurrent createFromCart → one must return 409
    // -------------------------------------------------------------------------
    @Test
    fun `concurrent order creation causes 409 for overselling`() {
        val product = createProduct("Limited Edition", 1)
        val customer1 = UUID.randomUUID()
        val customer2 = UUID.randomUUID()

        addItemToCart(customer1, product.id, 1, BigDecimal("50.00"))
        addItemToCart(customer2, product.id, 1, BigDecimal("50.00"))

        val executor = Executors.newFixedThreadPool(2)
        val futures = listOf(customer1, customer2).map { customerId ->
            executor.submit(Callable {
                client.post()
                    .uri("/api/v1/orders/from-cart/$customerId")
                    .retrieve()
                    .onStatus({ true }) { _, response -> }
                    .toBodilessEntity()
                    .statusCode
            })
        }

        val statuses = futures.map { it.get() }
        executor.shutdown()

        val created = statuses.count { it == HttpStatus.CREATED }
        val conflict = statuses.count { it == HttpStatus.CONFLICT }
        assertEquals(1, created, "Exactly one order should succeed")
        assertEquals(1, conflict, "Exactly one order should fail with 409")
    }

    // -------------------------------------------------------------------------
    // Cancel flow: create order → cancel → verify inventory released
    // -------------------------------------------------------------------------
    @Test
    fun `cancel order releases inventory`() {
        val product = createProduct("Gadget", 10)
        val customerId = UUID.randomUUID()
        addItemToCart(customerId, product.id, 3, BigDecimal("29.99"))

        val orderId = client.post()
            .uri("/api/v1/orders/from-cart/$customerId")
            .retrieve()
            .body(CreateOrderResponse::class.java)!!.orderId

        // Stock should be reserved
        val stockAfterOrder = client.get()
            .uri("/api/v1/products/${product.id}/stock")
            .retrieve()
            .body(StockResponse::class.java)!!
        assertEquals(7, stockAfterOrder.available)
        assertEquals(3, stockAfterOrder.reserved)

        // Cancel the order
        val cancelStatus = client.method(HttpMethod.DELETE)
            .uri("/api/v1/orders/$orderId/cancel")
            .contentType(MediaType.APPLICATION_JSON)
            .body(CancelOrderRequest("customer-request"))
            .retrieve()
            .toBodilessEntity()
            .statusCode
        assertEquals(HttpStatus.NO_CONTENT, cancelStatus)

        // Stock should be released
        val stockAfterCancel = client.get()
            .uri("/api/v1/products/${product.id}/stock")
            .retrieve()
            .body(StockResponse::class.java)!!
        assertEquals(10, stockAfterCancel.available)
        assertEquals(0, stockAfterCancel.reserved)
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------
    private fun createProduct(name: String, stock: Int): CreateProductResponse {
        val resp = client.post()
            .uri("/admin/products")
            .contentType(MediaType.APPLICATION_JSON)
            .body(CreateProductRequest(name, stock))
            .retrieve()
            .body(CreateProductResponse::class.java)!!
        assertNotNull(resp.id)
        return resp
    }

    private fun addItemToCart(customerId: UUID, productId: UUID, quantity: Int, unitPrice: BigDecimal) {
        client.post()
            .uri("/api/v1/carts/$customerId/items")
            .contentType(MediaType.APPLICATION_JSON)
            .body(AddItemRequest(productId, quantity, unitPrice))
            .retrieve()
            .toBodilessEntity()
    }
}
