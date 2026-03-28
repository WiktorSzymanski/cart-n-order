package pl.szymanski.wiktor.cart_n_order.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.szymanski.wiktor.cart_n_order.domain.exception.CartEmptyException
import pl.szymanski.wiktor.cart_n_order.domain.exception.InsufficientStockException
import pl.szymanski.wiktor.cart_n_order.domain.exception.OrderNotFoundException
import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderItem
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderSummary
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderUseCase
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.CartRepository
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.InventoryRepository
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.OrderRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID

@Service
@Transactional
class OrderService(
    private val orderRepository: OrderRepository,
    private val cartRepository: CartRepository,
    private val inventoryRepository: InventoryRepository
) : OrderUseCase {

    override fun createFromCart(customerId: UUID): UUID {
        val cart = cartRepository.findByCustomerId(customerId)
        if (cart == null || cart.items.isEmpty()) throw CartEmptyException(customerId)

        val orderId = UUID.randomUUID()
        val now = Instant.now()
        val orderItems = cart.items.map { item ->
            OrderItem(
                id = UUID.randomUUID(),
                orderId = orderId,
                productId = item.productId,
                quantity = item.quantity,
                unitPrice = item.unitPrice
            )
        }
        val order = Order(
            id = orderId,
            customerId = customerId,
            status = OrderStatus.NEW,
            items = orderItems,
            totalAmount = cart.totalAmount,
            createdAt = now,
            updatedAt = now
        )
        orderRepository.save(order)

        for (item in cart.items) {
            val reserved = inventoryRepository.reserveStock(item.productId, item.quantity)
            if (!reserved) {
                val inventory = inventoryRepository.findByProductId(item.productId)
                throw InsufficientStockException(item.productId, item.quantity, inventory?.availableQuantity ?: 0)
            }
        }

        cartRepository.deleteByCustomerId(customerId)
        return orderId
    }

    override fun confirm(orderId: UUID) {
        val order = findOrThrow(orderId)
        orderRepository.save(order.confirm())
    }

    override fun pay(orderId: UUID, paymentDetails: String) {
        val order = findOrThrow(orderId)
        orderRepository.save(order.pay())
    }

    override fun ship(orderId: UUID, trackingNumber: String) {
        val order = findOrThrow(orderId)
        orderRepository.save(order.ship())
    }

    override fun cancel(orderId: UUID, reason: String) {
        val order = findOrThrow(orderId)
        val cancelled = order.cancel()
        orderRepository.save(cancelled)
        for (item in order.items) {
            inventoryRepository.releaseStock(item.productId, item.quantity)
        }
    }

    @Transactional(readOnly = true)
    override fun getOrder(orderId: UUID): Order = findOrThrow(orderId)

    @Transactional(readOnly = true)
    override fun getCustomerOrders(
        customerId: UUID,
        statuses: List<OrderStatus>?,
        limit: Int,
        offset: Int
    ): Pair<List<Order>, Long> {
        val orders = orderRepository.findByCustomerId(customerId, statuses, limit, offset)
        val total = orderRepository.countByCustomerId(customerId, statuses)
        return Pair(orders, total)
    }

    @Transactional(readOnly = true)
    override fun getSummary(from: LocalDate, to: LocalDate, status: OrderStatus?): OrderSummary {
        val fromInstant = from.atStartOfDay().toInstant(ZoneOffset.UTC)
        val toInstant = to.plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
        return orderRepository.getSummary(fromInstant, toInstant, status)
    }

    private fun findOrThrow(orderId: UUID): Order =
        orderRepository.findById(orderId) ?: throw OrderNotFoundException(orderId)
}
