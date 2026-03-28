package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper

import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.ProductInventoryJpaEntity

object InventoryMapper {
    fun toDomain(entity: ProductInventoryJpaEntity) = Inventory(
        productId = entity.productId,
        availableQuantity = entity.availableQuantity,
        reservedQuantity = entity.reservedQuantity,
        totalQuantity = entity.totalQuantity
    )

    fun toEntity(domain: Inventory) = ProductInventoryJpaEntity(
        productId = domain.productId,
        availableQuantity = domain.availableQuantity,
        reservedQuantity = domain.reservedQuantity,
        totalQuantity = domain.totalQuantity
    )
}
