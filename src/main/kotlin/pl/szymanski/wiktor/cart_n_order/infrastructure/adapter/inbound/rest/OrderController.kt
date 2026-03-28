package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderUseCase
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CancelOrderRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateOrderResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.OrderItemDto
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.OrderResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.PayOrderRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.ShipOrderRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.SummaryResponse
import java.time.LocalDate
import java.util.UUID

@RestController
@RequestMapping("/api/v1/orders")
@Tag(name = "Order", description = "Order management")
class OrderController(private val orderUseCase: OrderUseCase) {

    @PostMapping("/from-cart/{customerId}")
    @Operation(summary = "Create order from cart")
    @ApiResponse(responseCode = "201", description = "Order created")
    @ApiResponse(responseCode = "409", description = "Insufficient stock")
    @ApiResponse(responseCode = "400", description = "Cart empty or invalid")
    fun createFromCart(@PathVariable customerId: UUID): ResponseEntity<CreateOrderResponse> {
        val orderId = orderUseCase.createFromCart(customerId)
        return ResponseEntity.status(HttpStatus.CREATED).body(CreateOrderResponse(orderId))
    }

    @PostMapping("/{orderId}/confirm")
    @Operation(summary = "Confirm order")
    @ApiResponse(responseCode = "204", description = "Order confirmed")
    fun confirm(@PathVariable orderId: UUID): ResponseEntity<Void> {
        orderUseCase.confirm(orderId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{orderId}/pay")
    @Operation(summary = "Pay for order")
    @ApiResponse(responseCode = "204", description = "Order paid")
    fun pay(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: PayOrderRequest
    ): ResponseEntity<Void> {
        orderUseCase.pay(orderId, request.paymentDetails)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{orderId}/ship")
    @Operation(summary = "Ship order")
    @ApiResponse(responseCode = "204", description = "Order shipped")
    fun ship(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: ShipOrderRequest
    ): ResponseEntity<Void> {
        orderUseCase.ship(orderId, request.trackingNumber)
        return ResponseEntity.noContent().build()
    }

    @DeleteMapping("/{orderId}/cancel")
    @Operation(summary = "Cancel order")
    @ApiResponse(responseCode = "204", description = "Order cancelled")
    fun cancel(
        @PathVariable orderId: UUID,
        @Valid @RequestBody request: CancelOrderRequest
    ): ResponseEntity<Void> {
        orderUseCase.cancel(orderId, request.reason)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/{orderId}")
    @Operation(summary = "Get order by ID")
    @ApiResponse(responseCode = "200", description = "Order details")
    @ApiResponse(responseCode = "404", description = "Order not found")
    fun getOrder(@PathVariable orderId: UUID): ResponseEntity<OrderResponse> {
        val order = orderUseCase.getOrder(orderId)
        return ResponseEntity.ok(order.toResponse())
    }

    @GetMapping("/summary")
    @Operation(summary = "Get order summary")
    @ApiResponse(responseCode = "200", description = "Order summary")
    fun getSummary(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @RequestParam(required = false) status: OrderStatus?
    ): ResponseEntity<SummaryResponse> {
        val summary = orderUseCase.getSummary(from, to, status)
        return ResponseEntity.ok(SummaryResponse(summary.totalOrders, summary.totalVolume))
    }
}

internal fun pl.szymanski.wiktor.cart_n_order.domain.model.Order.toResponse() = OrderResponse(
    id = id,
    customerId = customerId,
    status = status.name,
    items = items.map { OrderItemDto(it.id, it.productId, it.quantity, it.unitPrice) },
    totalAmount = totalAmount,
    createdAt = createdAt,
    updatedAt = updatedAt
)
