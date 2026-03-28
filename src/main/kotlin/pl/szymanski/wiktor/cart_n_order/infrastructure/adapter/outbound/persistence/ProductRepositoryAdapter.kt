package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.model.Product
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.ProductRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.ProductMapper
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository.ProductJpaRepository
import java.util.UUID

@Component
class ProductRepositoryAdapter(
    private val productJpaRepository: ProductJpaRepository
) : ProductRepository {

    override fun save(product: Product): Product =
        ProductMapper.toDomain(productJpaRepository.save(ProductMapper.toEntity(product)))

    override fun findById(id: UUID): Product? =
        productJpaRepository.findById(id).map { ProductMapper.toDomain(it) }.orElse(null)
}
