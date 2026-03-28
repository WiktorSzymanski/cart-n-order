package pl.szymanski.wiktor.cart_n_order.domain.model

import java.math.BigDecimal
import java.util.UUID

data class OrderItem(
    val id: UUID,
    val orderId: UUID,
    val productId: UUID,
    val quantity: Int,
    val unitPrice: BigDecimal
)
