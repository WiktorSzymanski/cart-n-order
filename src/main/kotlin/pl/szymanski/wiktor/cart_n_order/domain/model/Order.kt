package pl.szymanski.wiktor.cart_n_order.domain.model

import pl.szymanski.wiktor.cart_n_order.domain.event.OrderEvent
import pl.szymanski.wiktor.cart_n_order.domain.exception.InvalidOrderTransitionException
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class Order private constructor(
    val id: UUID,
    val customerId: UUID,
    val createdAt: Instant
) : AggregateRoot<OrderEvent>() {

    private var _status: OrderStatus = OrderStatus.NEW
    private var _items: List<OrderItem> = emptyList()
    private var _totalAmount: BigDecimal = BigDecimal.ZERO
    private var _updatedAt: Instant = createdAt

    val status: OrderStatus get() = _status
    val items: List<OrderItem> get() = _items
    val totalAmount: BigDecimal get() = _totalAmount
    val updatedAt: Instant get() = _updatedAt

    fun confirm(): Order {
        if (_status != OrderStatus.NEW) throw InvalidOrderTransitionException(_status, OrderStatus.CONFIRMED)
        raise(OrderEvent.OrderConfirmed(id))
        return this
    }

    fun pay(): Order {
        if (_status != OrderStatus.CONFIRMED) throw InvalidOrderTransitionException(_status, OrderStatus.PAID)
        raise(OrderEvent.OrderPaid(id))
        return this
    }

    fun ship(): Order {
        if (_status != OrderStatus.PAID) throw InvalidOrderTransitionException(_status, OrderStatus.SHIPPED)
        raise(OrderEvent.OrderShipped(id, trackingNumber = null))
        return this
    }

    fun cancel(): Order {
        if (_status == OrderStatus.SHIPPED) throw InvalidOrderTransitionException(_status, OrderStatus.CANCELLED)
        raise(OrderEvent.OrderCancelled(id, reason = null))
        return this
    }

    override fun applyEvent(event: OrderEvent) {
        when (event) {
            is OrderEvent.OrderPlaced -> {
                _status = OrderStatus.NEW
                _items = event.items.map { OrderItem(it.id, id, it.productId, it.quantity, it.unitPrice) }
                _totalAmount = event.totalAmount
                _updatedAt = event.occurredAt
            }
            is OrderEvent.OrderConfirmed -> {
                _status = OrderStatus.CONFIRMED
                _updatedAt = event.occurredAt
            }
            is OrderEvent.OrderPaid -> {
                _status = OrderStatus.PAID
                _updatedAt = event.occurredAt
            }
            is OrderEvent.OrderShipped -> {
                _status = OrderStatus.SHIPPED
                _updatedAt = event.occurredAt
            }
            is OrderEvent.OrderCancelled -> {
                _status = OrderStatus.CANCELLED
                _updatedAt = event.occurredAt
            }
        }
    }

    companion object {
        fun place(id: UUID, customerId: UUID, items: List<OrderItem>, totalAmount: BigDecimal): Order {
            val now = Instant.now()
            val order = Order(id, customerId, now)
            val itemData = items.map { OrderEvent.OrderItemData(it.id, it.productId, it.quantity, it.unitPrice) }
            order.raise(OrderEvent.OrderPlaced(id, customerId, itemData, totalAmount, now))
            return order
        }

        /** Reconstructs an Order from its full event history (optionally starting from a snapshot). */
        fun reconstitute(events: List<OrderEvent>, snapshot: OrderSnapshot?): Order {
            val order = if (snapshot != null) {
                Order(snapshot.id, snapshot.customerId, snapshot.createdAt).also { o ->
                    o._status = snapshot.status
                    o._items = snapshot.items
                    o._totalAmount = snapshot.totalAmount
                    o._updatedAt = snapshot.updatedAt
                    o.restoreVersion(snapshot.version)
                }
            } else {
                val placed = events.filterIsInstance<OrderEvent.OrderPlaced>().first()
                Order(placed.orderId, placed.customerId, placed.occurredAt)
            }
            events.forEach { order.replay(it) }
            return order
        }

        /** Creates an Order for read-only display from projection data (no events, no uncommitted changes). */
        fun fromProjection(
            id: UUID,
            customerId: UUID,
            status: OrderStatus,
            items: List<OrderItem>,
            totalAmount: BigDecimal,
            createdAt: Instant,
            updatedAt: Instant
        ): Order = Order(id, customerId, createdAt).also { o ->
            o._status = status
            o._items = items
            o._totalAmount = totalAmount
            o._updatedAt = updatedAt
        }
    }
}
