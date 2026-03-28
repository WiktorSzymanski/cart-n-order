package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "carts")
class CartJpaEntity(
    @Id
    @Column(name = "customer_id")
    val customerId: UUID,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    var items: String = "[]",

    @Column(name = "updated_at")
    var updatedAt: Instant = Instant.now()
)
