package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.ProductJpaEntity
import java.util.UUID

interface ProductJpaRepository : JpaRepository<ProductJpaEntity, UUID>
