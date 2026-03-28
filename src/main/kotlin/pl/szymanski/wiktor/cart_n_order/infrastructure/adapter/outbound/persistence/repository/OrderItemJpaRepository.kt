package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.OrderItemJpaEntity
import java.util.UUID

interface OrderItemJpaRepository : JpaRepository<OrderItemJpaEntity, UUID> {
    fun findAllByOrderId(orderId: UUID): List<OrderItemJpaEntity>
}
