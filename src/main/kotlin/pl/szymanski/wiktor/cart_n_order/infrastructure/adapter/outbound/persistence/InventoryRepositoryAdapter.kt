package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.InventoryRepository
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.InventoryMapper
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.repository.ProductInventoryJpaRepository
import java.util.UUID

@Component
class InventoryRepositoryAdapter(
    private val inventoryJpaRepository: ProductInventoryJpaRepository
) : InventoryRepository {

    override fun findByProductId(productId: UUID): Inventory? =
        inventoryJpaRepository.findById(productId).map { InventoryMapper.toDomain(it) }.orElse(null)

    override fun save(inventory: Inventory): Inventory =
        InventoryMapper.toDomain(inventoryJpaRepository.save(InventoryMapper.toEntity(inventory)))

    override fun reserveStock(productId: UUID, qty: Int): Boolean =
        inventoryJpaRepository.reserveStock(productId, qty) > 0

    override fun releaseStock(productId: UUID, qty: Int) =
        inventoryJpaRepository.releaseStock(productId, qty)
}
