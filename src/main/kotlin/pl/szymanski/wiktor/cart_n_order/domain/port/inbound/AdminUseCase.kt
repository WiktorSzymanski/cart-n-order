package pl.szymanski.wiktor.cart_n_order.domain.port.inbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Product
import java.util.UUID

interface AdminUseCase {
    fun createProduct(name: String, initialStock: Int): Product
    fun generateCustomers(count: Int): List<UUID>
}
