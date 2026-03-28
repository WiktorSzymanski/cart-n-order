package pl.szymanski.wiktor.cart_n_order.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Cart(
    val customerId: UUID,
    val items: List<CartItem>,
    val updatedAt: Instant = Instant.now()
) {
    val totalItems: Int get() = items.sumOf { it.quantity }
    val totalAmount: BigDecimal get() = items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }

    fun addOrUpdateItem(productId: UUID, quantity: Int, unitPrice: BigDecimal): Cart {
        val existing = items.find { it.productId == productId }
        val updated = if (existing != null) {
            items.map {
                if (it.productId == productId) it.copy(quantity = it.quantity + quantity)
                else it
            }
        } else {
            items + CartItem(productId, quantity, unitPrice)
        }
        return copy(items = updated, updatedAt = Instant.now())
    }

    fun removeItem(productId: UUID, quantity: Int?): Cart {
        val existing = items.find { it.productId == productId } ?: return this
        val updated = if (quantity == null || existing.quantity <= quantity) {
            items.filter { it.productId != productId }
        } else {
            items.map {
                if (it.productId == productId) it.copy(quantity = it.quantity - quantity)
                else it
            }
        }
        return copy(items = updated, updatedAt = Instant.now())
    }
}
