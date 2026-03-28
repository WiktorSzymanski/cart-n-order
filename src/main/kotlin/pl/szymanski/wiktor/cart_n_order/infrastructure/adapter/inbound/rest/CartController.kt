package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.CartUseCase
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.AddItemRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CartItemDto
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CartResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.RemoveItemRequest
import java.util.UUID

@RestController
@RequestMapping("/api/v1/carts")
@Tag(name = "Cart", description = "Cart management")
class CartController(private val cartUseCase: CartUseCase) {

    @PostMapping("/{customerId}/items")
    @Operation(summary = "Add item to cart")
    @ApiResponse(responseCode = "204", description = "Item added")
    @ApiResponse(responseCode = "400", description = "Invalid request")
    fun addItem(
        @PathVariable customerId: UUID,
        @Valid @RequestBody request: AddItemRequest
    ): ResponseEntity<Void> {
        cartUseCase.addItem(customerId, request.productId, request.quantity, request.unitPrice)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{customerId}/items/{productId}")
    @Operation(summary = "Remove item from cart")
    @ApiResponse(responseCode = "204", description = "Item removed")
    @ApiResponse(responseCode = "404", description = "Cart not found")
    fun removeItem(
        @PathVariable customerId: UUID,
        @PathVariable productId: UUID,
        @RequestBody(required = false) request: RemoveItemRequest?
    ): ResponseEntity<Void> {
        cartUseCase.removeItem(customerId, productId, request?.quantity)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{customerId}")
    @Operation(summary = "Get cart")
    @ApiResponse(responseCode = "200", description = "Cart contents")
    fun getCart(@PathVariable customerId: UUID): ResponseEntity<CartResponse> {
        val cart = cartUseCase.getCart(customerId)
        val response = CartResponse(
            items = cart.items.map { CartItemDto(it.productId, it.quantity, it.unitPrice) },
            totalItems = cart.totalItems,
            totalAmount = cart.totalAmount
        )
        return ResponseEntity.ok(response)
    }
}
