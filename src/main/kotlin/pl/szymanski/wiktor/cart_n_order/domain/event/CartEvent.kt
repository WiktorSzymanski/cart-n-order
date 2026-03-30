package pl.szymanski.wiktor.cart_n_order.domain.event

import pl.szymanski.wiktor.cart_n_order.domain.model.DomainEvent
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

sealed class CartEvent : DomainEvent() {

    data class ItemAddedOrUpdated(
        val customerId: UUID,
        val productId: UUID,
        val quantity: Int,
        val unitPrice: BigDecimal,
        val occurredAt: Instant = Instant.now()
    ) : CartEvent()

    data class ItemRemoved(
        val customerId: UUID,
        val productId: UUID,
        val quantity: Int?,
        val occurredAt: Instant = Instant.now()
    ) : CartEvent()

    data class CartCleared(
        val customerId: UUID,
        val occurredAt: Instant = Instant.now()
    ) : CartEvent()
}
