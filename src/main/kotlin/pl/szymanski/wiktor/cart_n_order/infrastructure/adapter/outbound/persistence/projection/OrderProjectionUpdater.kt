package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.projection

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.event.OrderEvent
import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import java.time.Instant
import java.util.UUID

@Component
class OrderProjectionUpdater(private val jdbc: NamedParameterJdbcTemplate) {

    fun handle(order: Order, events: List<OrderEvent>) {
        events.forEach { event ->
            when (event) {
                is OrderEvent.OrderPlaced -> onPlaced(order, event)
                is OrderEvent.OrderConfirmed -> onStatusChanged(event.orderId, OrderStatus.CONFIRMED, event.occurredAt)
                is OrderEvent.OrderPaid -> onStatusChanged(event.orderId, OrderStatus.PAID, event.occurredAt)
                is OrderEvent.OrderShipped -> onStatusChanged(event.orderId, OrderStatus.SHIPPED, event.occurredAt)
                is OrderEvent.OrderCancelled -> onStatusChanged(event.orderId, OrderStatus.CANCELLED, event.occurredAt)
            }
        }
    }

    private fun onPlaced(order: Order, event: OrderEvent.OrderPlaced) {
        jdbc.update(
            """
            INSERT INTO proj_orders (id, customer_id, status, total_amount, created_at, updated_at)
            VALUES (:id, :customerId, :status, :totalAmount, :createdAt, :updatedAt)
            """,
            mapOf(
                "id" to event.orderId,
                "customerId" to event.customerId,
                "status" to OrderStatus.NEW.name,
                "totalAmount" to event.totalAmount,
                "createdAt" to java.sql.Timestamp.from(event.occurredAt),
                "updatedAt" to java.sql.Timestamp.from(event.occurredAt)
            )
        )
        val batchParams = order.items.map { item ->
            mapOf(
                "id" to item.id,
                "orderId" to event.orderId,
                "productId" to item.productId,
                "quantity" to item.quantity,
                "unitPrice" to item.unitPrice
            )
        }.toTypedArray()
        if (batchParams.isNotEmpty()) {
            jdbc.batchUpdate(
                """
                INSERT INTO proj_order_items (id, order_id, product_id, quantity, unit_price)
                VALUES (:id, :orderId, :productId, :quantity, :unitPrice)
                """,
                batchParams
            )
        }
    }

    private fun onStatusChanged(orderId: UUID, status: OrderStatus, updatedAt: Instant) {
        jdbc.update(
            "UPDATE proj_orders SET status = :status, updated_at = :updatedAt WHERE id = :id",
            mapOf("status" to status.name, "updatedAt" to java.sql.Timestamp.from(updatedAt), "id" to orderId)
        )
    }
}
