package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.ProductInventoryJpaEntity
import java.util.UUID

interface ProductInventoryJpaRepository : JpaRepository<ProductInventoryJpaEntity, UUID> {

    @Modifying
    @Query("""
        UPDATE ProductInventoryJpaEntity i
        SET i.availableQuantity = i.availableQuantity - :qty,
            i.reservedQuantity = i.reservedQuantity + :qty
        WHERE i.productId = :productId AND i.availableQuantity >= :qty
    """)
    fun reserveStock(@Param("productId") productId: UUID, @Param("qty") qty: Int): Int

    @Modifying
    @Query("""
        UPDATE ProductInventoryJpaEntity i
        SET i.availableQuantity = i.availableQuantity + :qty,
            i.reservedQuantity = i.reservedQuantity - :qty
        WHERE i.productId = :productId
    """)
    fun releaseStock(@Param("productId") productId: UUID, @Param("qty") qty: Int)
}
