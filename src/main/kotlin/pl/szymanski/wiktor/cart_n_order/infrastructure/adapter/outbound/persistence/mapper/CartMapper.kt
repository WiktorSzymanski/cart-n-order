package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper

import tools.jackson.databind.ObjectMapper
import tools.jackson.module.kotlin.readValue
import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import pl.szymanski.wiktor.cart_n_order.domain.model.CartItem
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.CartJpaEntity
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class CartItemJson(val productId: UUID, val quantity: Int, val unitPrice: BigDecimal)

class CartMapper(private val objectMapper: ObjectMapper) {

    fun toDomain(entity: CartJpaEntity): Cart {
        val items: List<CartItemJson> = objectMapper.readValue(entity.items)
        return Cart(
            customerId = entity.customerId,
            items = items.map { CartItem(it.productId, it.quantity, it.unitPrice) },
            updatedAt = entity.updatedAt
        )
    }

    fun toEntity(domain: Cart): CartJpaEntity {
        val itemsJson = objectMapper.writeValueAsString(
            domain.items.map { CartItemJson(it.productId, it.quantity, it.unitPrice) }
        )
        return CartJpaEntity(
            customerId = domain.customerId,
            items = itemsJson,
            updatedAt = domain.updatedAt
        )
    }

    fun emptyCart(customerId: UUID) = Cart(
        customerId = customerId,
        items = emptyList(),
        updatedAt = Instant.now()
    )
}
