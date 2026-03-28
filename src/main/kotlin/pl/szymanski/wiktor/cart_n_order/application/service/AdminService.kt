package pl.szymanski.wiktor.cart_n_order.application.service

import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import pl.szymanski.wiktor.cart_n_order.domain.model.Inventory
import pl.szymanski.wiktor.cart_n_order.domain.model.Product
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.AdminUseCase
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.InventoryRepository
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.ProductRepository
import java.time.Instant
import java.util.UUID

@Service
@Transactional
class AdminService(
    private val productRepository: ProductRepository,
    private val inventoryRepository: InventoryRepository
) : AdminUseCase {

    override fun createProduct(name: String, initialStock: Int): Product {
        val product = Product(id = UUID.randomUUID(), name = name, createdAt = Instant.now())
        val saved = productRepository.save(product)
        inventoryRepository.save(
            Inventory(
                productId = saved.id,
                availableQuantity = initialStock,
                reservedQuantity = 0,
                totalQuantity = initialStock
            )
        )
        return saved
    }

    override fun generateCustomers(count: Int): List<UUID> =
        (1..count).map { UUID.randomUUID() }
}
