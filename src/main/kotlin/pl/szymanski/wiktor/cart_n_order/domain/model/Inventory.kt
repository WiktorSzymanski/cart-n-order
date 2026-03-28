package pl.szymanski.wiktor.cart_n_order.domain.model

import java.util.UUID

data class Inventory(
    val productId: UUID,
    val availableQuantity: Int,
    val reservedQuantity: Int,
    val totalQuantity: Int
)
