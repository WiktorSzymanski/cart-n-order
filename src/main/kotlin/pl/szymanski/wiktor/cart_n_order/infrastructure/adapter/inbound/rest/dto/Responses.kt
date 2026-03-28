package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CartItemDto(
    val productId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class CartResponse(
    val items: List<CartItemDto>,
    val totalItems: Int,
    val totalAmount: BigDecimal
)

data class OrderItemDto(
    val id: UUID,
    val productId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal
)

data class OrderResponse(
    val id: UUID,
    val customerId: UUID,
    val status: String,
    val items: List<OrderItemDto>,
    val totalAmount: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
)

data class CreateOrderResponse(val orderId: UUID)

data class OrderListResponse(val orders: List<OrderResponse>, val total: Long)

data class StockResponse(val available: Int, val reserved: Int)

data class SummaryResponse(val totalOrders: Long, val totalVolume: BigDecimal)

data class CreateProductResponse(val id: UUID, val name: String)

data class GenerateCustomersResponse(val customerIds: List<UUID>)

data class ErrorResponse(val error: String, val code: String)
