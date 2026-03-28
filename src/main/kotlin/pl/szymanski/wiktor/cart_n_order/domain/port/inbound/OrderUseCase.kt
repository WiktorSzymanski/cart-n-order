package pl.szymanski.wiktor.cart_n_order.domain.port.inbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

data class OrderSummary(val totalOrders: Long, val totalVolume: BigDecimal)

interface OrderUseCase {
    fun createFromCart(customerId: UUID): UUID
    fun confirm(orderId: UUID)
    fun pay(orderId: UUID, paymentDetails: String)
    fun ship(orderId: UUID, trackingNumber: String)
    fun cancel(orderId: UUID, reason: String)
    fun getOrder(orderId: UUID): Order
    fun getCustomerOrders(customerId: UUID, statuses: List<OrderStatus>?, limit: Int, offset: Int): Pair<List<Order>, Long>
    fun getSummary(from: LocalDate, to: LocalDate, status: OrderStatus?): OrderSummary
}
