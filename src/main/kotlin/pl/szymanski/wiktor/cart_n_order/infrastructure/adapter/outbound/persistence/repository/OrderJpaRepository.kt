package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository

import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.OrderJpaEntity
import java.time.Instant
import java.util.UUID

interface OrderJpaRepository : JpaRepository<OrderJpaEntity, UUID> {

    @Query("""
        SELECT o FROM OrderJpaEntity o
        WHERE o.customerId = :customerId
          AND (:#{#statuses == null} = true OR o.status IN :statuses)
        ORDER BY o.createdAt DESC
    """)
    fun findByCustomerIdFiltered(
        @Param("customerId") customerId: UUID,
        @Param("statuses") statuses: List<OrderStatus>?,
        pageable: Pageable
    ): List<OrderJpaEntity>

    @Query("""
        SELECT COUNT(o) FROM OrderJpaEntity o
        WHERE o.customerId = :customerId
          AND (:#{#statuses == null} = true OR o.status IN :statuses)
    """)
    fun countByCustomerIdFiltered(
        @Param("customerId") customerId: UUID,
        @Param("statuses") statuses: List<OrderStatus>?
    ): Long

    @Query("""
        SELECT COUNT(o), COALESCE(SUM(o.totalAmount), 0)
        FROM OrderJpaEntity o
        WHERE o.createdAt >= :from
          AND o.createdAt <= :to
          AND (:#{#status == null} = true OR o.status = :status)
    """)
    fun getSummaryRaw(
        @Param("from") from: Instant,
        @Param("to") to: Instant,
        @Param("status") status: OrderStatus?
    ): List<Any>
}
