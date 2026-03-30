package pl.szymanski.wiktor.cart_n_order.domain.model

import java.time.Instant
import java.util.UUID

data class CartSnapshot(
    val customerId: UUID,
    val items: List<CartItem>,
    val updatedAt: Instant,
    val version: Long
)
