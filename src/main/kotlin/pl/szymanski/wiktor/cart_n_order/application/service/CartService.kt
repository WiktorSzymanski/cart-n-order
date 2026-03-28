package pl.szymanski.wiktor.cart_n_order.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.CartUseCase
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.CartRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.CartMapper
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class CartService(
    private val cartRepository: CartRepository,
    private val cartMapper: CartMapper
) : CartUseCase {

    override fun addItem(customerId: UUID, productId: UUID, quantity: Int, unitPrice: BigDecimal) {
        val cart = cartRepository.findByCustomerId(customerId)
            ?: cartMapper.emptyCart(customerId)
        val updated = cart.addOrUpdateItem(productId, quantity, unitPrice)
        cartRepository.save(updated)
    }

    override fun removeItem(customerId: UUID, productId: UUID, quantity: Int?) {
        val cart = cartRepository.findByCustomerId(customerId)
            ?: cartMapper.emptyCart(customerId)
        val updated = cart.removeItem(productId, quantity)
        if (updated.items.isEmpty()) {
            cartRepository.deleteByCustomerId(customerId)
        } else {
            cartRepository.save(updated)
        }
    }

    @Transactional(readOnly = true)
    override fun getCart(customerId: UUID): Cart =
        cartRepository.findByCustomerId(customerId)
            ?: cartMapper.emptyCart(customerId)
}
