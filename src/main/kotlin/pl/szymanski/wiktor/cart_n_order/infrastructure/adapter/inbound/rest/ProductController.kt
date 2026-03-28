package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.ProductUseCase
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.StockResponse
import java.util.UUID

@RestController
@RequestMapping("/api/v1/products")
@Tag(name = "Product", description = "Product queries")
class ProductController(private val productUseCase: ProductUseCase) {

    @GetMapping("/{productId}/stock")
    @Operation(summary = "Get product stock")
    @ApiResponse(responseCode = "200", description = "Stock levels")
    @ApiResponse(responseCode = "404", description = "Product not found")
    fun getStock(@PathVariable productId: UUID): ResponseEntity<StockResponse> {
        val inventory = productUseCase.getStock(productId)
        return ResponseEntity.ok(StockResponse(inventory.availableQuantity, inventory.reservedQuantity))
    }
}
