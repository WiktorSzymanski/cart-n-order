package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.math.BigDecimal
import java.util.UUID

data class AddItemRequest(
    @field:NotNull val productId: UUID,
    @field:Min(1) val quantity: Int,
    @field:NotNull val unitPrice: BigDecimal
)

data class RemoveItemRequest(
    @field:Min(1) val quantity: Int? = null
)

data class PayOrderRequest(
    @field:NotBlank val paymentDetails: String
)

data class ShipOrderRequest(
    @field:NotBlank val trackingNumber: String
)

data class CancelOrderRequest(
    @field:NotBlank val reason: String
)

data class CreateProductRequest(
    @field:NotBlank val name: String,
    @field:Min(0) val initialStock: Int
)

data class GenerateCustomersRequest(
    @field:Min(1) val count: Int
)
