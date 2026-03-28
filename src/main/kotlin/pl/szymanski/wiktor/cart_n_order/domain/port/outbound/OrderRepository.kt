package pl.szymanski.wiktor.cart_n_order.domain.port.outbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderSummary
import java.time.Instant
import java.util.UUID

interface OrderRepository {
    fun findById(id: UUID): Order?
    fun save(order: Order): Order
    fun findByCustomerId(customerId: UUID, statuses: List<OrderStatus>?, limit: Int, offset: Int): List<Order>
    fun countByCustomerId(customerId: UUID, statuses: List<OrderStatus>?): Long
    fun getSummary(from: Instant, to: Instant, status: OrderStatus?): OrderSummary
}
