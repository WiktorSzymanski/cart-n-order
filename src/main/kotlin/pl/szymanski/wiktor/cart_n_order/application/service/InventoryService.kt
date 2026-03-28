package pl.szymanski.wiktor.cart_n_order.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.szymanski.wiktor.cart_n_order.domain.exception.ProductNotFoundException
import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.ProductUseCase
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.InventoryRepository
import java.util.UUID

@Service
@Transactional(readOnly = true)
class InventoryService(
    private val inventoryRepository: InventoryRepository
) : ProductUseCase {

    override fun getStock(productId: UUID): Inventory =
        inventoryRepository.findByProductId(productId) ?: throw ProductNotFoundException(productId)
}
