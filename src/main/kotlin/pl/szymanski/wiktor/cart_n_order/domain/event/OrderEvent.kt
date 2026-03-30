package pl.szymanski.wiktor.cart_n_order.domain.event

import pl.szymanski.wiktor.cart_n_order.domain.model.DomainEvent
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

sealed class OrderEvent : DomainEvent() {

    data class OrderItemData(
        val id: UUID,
        val productId: UUID,
        val quantity: Int,
        val unitPrice: BigDecimal
    )

    data class OrderPlaced(
        val orderId: UUID,
        val customerId: UUID,
        val items: List<OrderItemData>,
        val totalAmount: BigDecimal,
        val occurredAt: Instant = Instant.now()
    ) : OrderEvent()

    data class OrderConfirmed(
        val orderId: UUID,
        val occurredAt: Instant = Instant.now()
    ) : OrderEvent()

    data class OrderPaid(
        val orderId: UUID,
        val occurredAt: Instant = Instant.now()
    ) : OrderEvent()

    data class OrderShipped(
        val orderId: UUID,
        val trackingNumber: String?,
        val occurredAt: Instant = Instant.now()
    ) : OrderEvent()

    data class OrderCancelled(
        val orderId: UUID,
        val reason: String?,
        val occurredAt: Instant = Instant.now()
    ) : OrderEvent()
}
