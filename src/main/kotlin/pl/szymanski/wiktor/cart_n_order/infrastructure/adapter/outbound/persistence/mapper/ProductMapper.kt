package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper

import pl.szymanski.wiktor.cart_n_order.domain.model.Product
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.ProductJpaEntity

object ProductMapper {
    fun toDomain(entity: ProductJpaEntity) = Product(
        id = entity.id,
        name = entity.name,
        createdAt = entity.createdAt
    )

    fun toEntity(domain: Product) = ProductJpaEntity(
        id = domain.id,
        name = domain.name,
        createdAt = domain.createdAt
    )
}
