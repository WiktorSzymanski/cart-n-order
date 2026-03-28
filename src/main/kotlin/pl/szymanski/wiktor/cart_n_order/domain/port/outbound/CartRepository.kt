package pl.szymanski.wiktor.cart_n_order.domain.port.outbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import java.util.UUID

interface CartRepository {
    fun findByCustomerId(customerId: UUID): Cart?
    fun save(cart: Cart): Cart
    fun deleteByCustomerId(customerId: UUID)
}
