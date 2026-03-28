package pl.szymanski.wiktor.cart_n_order.domain.port.inbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import java.util.UUID

interface ProductUseCase {
    fun getStock(productId: UUID): Inventory
}
