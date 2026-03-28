package pl.szymanski.wiktor.cart_n_order.domain.port.outbound

import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import java.util.UUID

interface InventoryRepository {
    fun findByProductId(productId: UUID): Inventory?
    fun save(inventory: Inventory): Inventory
    fun reserveStock(productId: UUID, qty: Int): Boolean
    fun releaseStock(productId: UUID, qty: Int)
}
