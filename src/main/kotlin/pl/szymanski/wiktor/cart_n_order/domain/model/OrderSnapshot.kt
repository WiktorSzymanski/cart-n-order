package pl.szymanski.wiktor.cart_n_order.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class OrderSnapshot(
    val id: UUID,
    val customerId: UUID,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val totalAmount: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant,
    val version: Long
)
