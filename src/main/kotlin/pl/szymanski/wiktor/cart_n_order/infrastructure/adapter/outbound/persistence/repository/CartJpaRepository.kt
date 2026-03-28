package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository

import org.springframework.data.jpa.repository.JpaRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.entity.CartJpaEntity
import java.util.UUID

interface CartJpaRepository : JpaRepository<CartJpaEntity, UUID>
