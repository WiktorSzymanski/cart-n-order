package pl.szymanski.wiktor.cart_n_order.domain.model

import java.time.Instant
import java.util.UUID

data class Product(
    val id: UUID,
    val name: String,
    val createdAt: Instant
)
