package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.event.OrderEvent
import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderItem
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderSnapshot
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderSummary
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.EventStore
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.OrderRepository
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.StoredEvent
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.projection.OrderProjectionUpdater
import tools.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Component
class OrderRepositoryAdapter(
    private val eventStore: EventStore,
    private val orderProjectionUpdater: OrderProjectionUpdater,
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : OrderRepository {

    companion object {
        const val AGGREGATE_TYPE = "Order"
        const val SNAPSHOT_THRESHOLD = 10
    }

    override fun findById(id: UUID): Order? {
        val snapshotRecord = eventStore.loadSnapshot(id, AGGREGATE_TYPE)
        val afterVersion = snapshotRecord?.version ?: 0L
        val storedEvents = eventStore.loadEvents(id, afterVersion)

        if (snapshotRecord == null && storedEvents.isEmpty()) return null

        val orderEvents = storedEvents.map { deserialize(it) }
        val snapshot = snapshotRecord?.let { objectMapper.readValue(it.payload, OrderSnapshot::class.java) }
        return Order.reconstitute(orderEvents, snapshot)
    }

    override fun save(order: Order): Order {
        val uncommitted = order.uncommittedEvents
        if (uncommitted.isEmpty()) return order

        val expectedVersion = order.version - uncommitted.size
        eventStore.appendEvents(order.id, AGGREGATE_TYPE, uncommitted, expectedVersion)
        orderProjectionUpdater.handle(order, uncommitted)

        if (order.version % SNAPSHOT_THRESHOLD == 0L) {
            val snapshotPayload = objectMapper.writeValueAsString(
                OrderSnapshot(
                    id = order.id,
                    customerId = order.customerId,
                    status = order.status,
                    items = order.items,
                    totalAmount = order.totalAmount,
                    createdAt = order.createdAt,
                    updatedAt = order.updatedAt,
                    version = order.version
                )
            )
            eventStore.saveSnapshot(order.id, AGGREGATE_TYPE, order.version, snapshotPayload)
        }

        order.markCommitted()
        return order
    }

    override fun findByCustomerId(
        customerId: UUID,
        statuses: List<OrderStatus>?,
        limit: Int,
        offset: Int
    ): List<Order> {
        val statusFilter = if (statuses.isNullOrEmpty()) "" else "AND o.status IN (:statuses)"
        val sql = """
            SELECT o.id, o.customer_id, o.status, o.total_amount, o.created_at, o.updated_at,
                   i.id AS item_id, i.product_id, i.quantity, i.unit_price
            FROM proj_orders o
            LEFT JOIN proj_order_items i ON i.order_id = o.id
            WHERE o.customer_id = :customerId $statusFilter
            ORDER BY o.created_at DESC, o.id, i.id
            LIMIT :limit OFFSET :offset
        """
        val params = mutableMapOf<String, Any?>(
            "customerId" to customerId,
            "limit" to limit,
            "offset" to offset
        )
        if (!statuses.isNullOrEmpty()) params["statuses"] = statuses.map { it.name }

        return extractOrders(jdbc.queryForList(sql, params))
    }

    override fun countByCustomerId(customerId: UUID, statuses: List<OrderStatus>?): Long {
        val statusFilter = if (statuses.isNullOrEmpty()) "" else "AND status IN (:statuses)"
        val sql = "SELECT COUNT(*) FROM proj_orders WHERE customer_id = :customerId $statusFilter"
        val params = mutableMapOf<String, Any?>("customerId" to customerId)
        if (!statuses.isNullOrEmpty()) params["statuses"] = statuses.map { it.name }
        return jdbc.queryForObject(sql, params, Long::class.java) ?: 0L
    }

    override fun getSummary(from: Instant, to: Instant, status: OrderStatus?): OrderSummary {
        val statusFilter = if (status == null) "" else "AND status = :status"
        val sql = """
            SELECT COUNT(*), COALESCE(SUM(total_amount), 0)
            FROM proj_orders
            WHERE created_at >= :from AND created_at < :to $statusFilter
        """
        val params = mutableMapOf<String, Any?>("from" to java.sql.Timestamp.from(from), "to" to java.sql.Timestamp.from(to))
        if (status != null) params["status"] = status.name

        val row = jdbc.queryForList(sql, params).firstOrNull()
            ?: return OrderSummary(0L, BigDecimal.ZERO)

        val values = row.values.toList()
        return OrderSummary(
            totalOrders = (values[0] as Number).toLong(),
            totalVolume = (values[1] as? BigDecimal) ?: BigDecimal.ZERO
        )
    }

    private fun extractOrders(rows: List<Map<String, Any?>>): List<Order> {
        if (rows.isEmpty()) return emptyList()

        val ordersMap = linkedMapOf<UUID, Pair<Map<String, Any?>, MutableList<OrderItem>>>()
        for (row in rows) {
            val orderId = row["id"] as UUID
            val entry = ordersMap.getOrPut(orderId) { Pair(row, mutableListOf()) }
            val itemId = row["item_id"] as? UUID
            if (itemId != null) {
                entry.second.add(
                    OrderItem(
                        id = itemId,
                        orderId = orderId,
                        productId = row["product_id"] as UUID,
                        quantity = (row["quantity"] as Number).toInt(),
                        unitPrice = row["unit_price"] as BigDecimal
                    )
                )
            }
        }

        return ordersMap.map { (orderId, pair) ->
            val (row, items) = pair
            Order.fromProjection(
                id = orderId,
                customerId = row["customer_id"] as UUID,
                status = OrderStatus.valueOf(row["status"] as String),
                items = items,
                totalAmount = row["total_amount"] as BigDecimal,
                createdAt = (row["created_at"] as java.sql.Timestamp).toInstant(),
                updatedAt = (row["updated_at"] as java.sql.Timestamp).toInstant()
            )
        }
    }

    private fun deserialize(stored: StoredEvent): OrderEvent = when (stored.eventType) {
        "OrderPlaced" -> objectMapper.readValue(stored.payload, OrderEvent.OrderPlaced::class.java)
        "OrderConfirmed" -> objectMapper.readValue(stored.payload, OrderEvent.OrderConfirmed::class.java)
        "OrderPaid" -> objectMapper.readValue(stored.payload, OrderEvent.OrderPaid::class.java)
        "OrderShipped" -> objectMapper.readValue(stored.payload, OrderEvent.OrderShipped::class.java)
        "OrderCancelled" -> objectMapper.readValue(stored.payload, OrderEvent.OrderCancelled::class.java)
        else -> throw IllegalStateException("Unknown order event type: ${stored.eventType}")
    }
}
