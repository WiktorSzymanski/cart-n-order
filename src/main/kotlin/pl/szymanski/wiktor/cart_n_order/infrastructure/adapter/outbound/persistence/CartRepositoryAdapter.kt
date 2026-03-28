package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.CartRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.CartMapper
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository.CartJpaRepository
import java.util.UUID

@Component
class CartRepositoryAdapter(
    private val cartJpaRepository: CartJpaRepository,
    private val cartMapper: CartMapper
) : CartRepository {

    override fun findByCustomerId(customerId: UUID): Cart? =
        cartJpaRepository.findById(customerId).map { cartMapper.toDomain(it) }.orElse(null)

    override fun save(cart: Cart): Cart {
        val entity = cartMapper.toEntity(cart)
        return cartMapper.toDomain(cartJpaRepository.save(entity))
    }

    override fun deleteByCustomerId(customerId: UUID) =
        cartJpaRepository.deleteById(customerId)
}
