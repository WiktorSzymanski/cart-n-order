package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.util.UUID

@Entity
@Table(name = "products")
class ProductJpaEntity(
    @Id
    val id: UUID,

    @Column(nullable = false)
    val name: String,

    @Column(name = "created_at")
    val createdAt: Instant = Instant.now()
)
