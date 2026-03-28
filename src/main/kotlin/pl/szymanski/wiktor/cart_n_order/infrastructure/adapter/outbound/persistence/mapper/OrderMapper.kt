package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper

import pl.szymanski.wiktor.cart_n_order.domain.model.Order
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderItem
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.OrderItemJpaEntity
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.OrderJpaEntity

object OrderMapper {
    fun toDomain(entity: OrderJpaEntity, items: List<OrderItemJpaEntity>) = Order(
        id = entity.id,
        customerId = entity.customerId,
        status = entity.status,
        items = items.map { itemToDomain(it) },
        totalAmount = entity.totalAmount,
        createdAt = entity.createdAt,
        updatedAt = entity.updatedAt
    )

    fun toEntity(domain: Order) = OrderJpaEntity(
        id = domain.id,
        customerId = domain.customerId,
        status = domain.status,
        totalAmount = domain.totalAmount,
        createdAt = domain.createdAt,
        updatedAt = domain.updatedAt
    )

    fun itemToDomain(entity: OrderItemJpaEntity) = OrderItem(
        id = entity.id,
        orderId = entity.order.id,
        productId = entity.productId,
        quantity = entity.quantity,
        unitPrice = entity.unitPrice
    )
}
