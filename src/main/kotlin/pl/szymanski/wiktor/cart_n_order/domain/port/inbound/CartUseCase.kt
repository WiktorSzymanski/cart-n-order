package pl.szymanski.wiktor.cart_n_order.domain.port.inbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import java.math.BigDecimal
import java.util.UUID

interface CartUseCase {
    fun addItem(customerId: UUID, productId: UUID, quantity: Int, unitPrice: BigDecimal)
    fun removeItem(customerId: UUID, productId: UUID, quantity: Int?)
    fun getCart(customerId: UUID): Cart
}
