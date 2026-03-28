package pl.szymanski.wiktor.cart_n_order.domain.port.outbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Product
import java.util.UUID

interface ProductRepository {
    fun save(product: Product): Product
    fun findById(id: UUID): Product?
}
