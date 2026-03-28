package pl.szymanski.wiktor.cart_n_order.domain.model

import pl.szymanski.wiktor.cart_n_order.domain.exception.InvalidOrderTransitionException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class Order(
    val id: UUID,
    val customerId: UUID,
    val status: OrderStatus,
    val items: List<OrderItem>,
    val totalAmount: BigDecimal,
    val createdAt: Instant,
    val updatedAt: Instant
) {
    fun confirm(): Order = transition(OrderStatus.CONFIRMED, OrderStatus.NEW)
    fun pay(): Order = transition(OrderStatus.PAID, OrderStatus.CONFIRMED)
    fun ship(): Order = transition(OrderStatus.SHIPPED, OrderStatus.PAID)

    fun cancel(): Order {
        if (status == OrderStatus.SHIPPED) throw InvalidOrderTransitionException(status, OrderStatus.CANCELLED)
        return copy(status = OrderStatus.CANCELLED, updatedAt = Instant.now())
    }

    private fun transition(to: OrderStatus, vararg from: OrderStatus): Order {
        if (status !in from) throw InvalidOrderTransitionException(status, to)
        return copy(status = to, updatedAt = Instant.now())
    }
}
