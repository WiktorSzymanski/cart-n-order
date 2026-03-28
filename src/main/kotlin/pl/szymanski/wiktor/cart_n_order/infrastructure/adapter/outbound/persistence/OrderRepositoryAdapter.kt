package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderSummary
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.OrderRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.OrderItemJpaEntity
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.OrderMapper
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository.OrderItemJpaRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository.OrderJpaRepository
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Component
class OrderRepositoryAdapter(
    private val orderJpaRepository: OrderJpaRepository,
    private val orderItemJpaRepository: OrderItemJpaRepository
) : OrderRepository {

    override fun findById(id: UUID): Order? {
        val entity = orderJpaRepository.findById(id).orElse(null) ?: return null
        val items = orderItemJpaRepository.findAllByOrderId(id)
        return OrderMapper.toDomain(entity, items)
    }

    override fun save(order: Order): Order {
        val orderEntity = orderJpaRepository.save(OrderMapper.toEntity(order))
        val existingItemIds = orderItemJpaRepository.findAllByOrderId(order.id).map { it.id }.toSet()
        val newItems = order.items.filter { it.id !in existingItemIds }
        if (newItems.isNotEmpty()) {
            val itemEntities = newItems.map { item ->
                OrderItemJpaEntity(
                    id = item.id,
                    order = orderEntity,
                    productId = item.productId,
                    quantity = item.quantity,
                    unitPrice = item.unitPrice
                )
            }
            orderItemJpaRepository.saveAll(itemEntities)
        }
        val allItems = orderItemJpaRepository.findAllByOrderId(order.id)
        return OrderMapper.toDomain(orderEntity, allItems)
    }

    override fun findByCustomerId(
        customerId: UUID,
        statuses: List<OrderStatus>?,
        limit: Int,
        offset: Int
    ): List<Order> {
        val pageable = PageRequest.of(offset / limit, limit, Sort.by(Sort.Direction.DESC, "createdAt"))
        return orderJpaRepository.findByCustomerIdFiltered(customerId, statuses, pageable)
            .map { entity ->
                val items = orderItemJpaRepository.findAllByOrderId(entity.id)
                OrderMapper.toDomain(entity, items)
            }
    }

    override fun countByCustomerId(customerId: UUID, statuses: List<OrderStatus>?): Long =
        orderJpaRepository.countByCustomerIdFiltered(customerId, statuses)

    override fun getSummary(from: Instant, to: Instant, status: OrderStatus?): OrderSummary {
        val result = orderJpaRepository.getSummaryRaw(from, to, status)
        if (result.isEmpty()) return OrderSummary(0L, BigDecimal.ZERO)
        val row = result[0] as Array<*>
        return OrderSummary(
            totalOrders = (row[0] as Long),
            totalVolume = (row[1] as? BigDecimal) ?: BigDecimal.ZERO
        )
    }
}
