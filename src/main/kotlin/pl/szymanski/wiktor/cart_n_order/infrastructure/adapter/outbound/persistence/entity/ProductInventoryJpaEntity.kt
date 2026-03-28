package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.util.UUID

@Entity
@Table(name = "product_inventory")
class ProductInventoryJpaEntity(
    @Id
    @Column(name = "product_id")
    val productId: UUID,

    @Column(name = "available_quantity", nullable = false)
    var availableQuantity: Int = 0,

    @Column(name = "reserved_quantity", nullable = false)
    var reservedQuantity: Int = 0,

    @Column(name = "total_quantity", nullable = false)
    var totalQuantity: Int = 0
)
