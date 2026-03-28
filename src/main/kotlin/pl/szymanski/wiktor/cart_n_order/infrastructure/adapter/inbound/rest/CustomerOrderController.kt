package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.OrderUseCase
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.OrderListResponse
import java.util.UUID

@RestController
@RequestMapping("/api/v1/customers")
@Tag(name = "Customer Orders", description = "Customer order queries")
class CustomerOrderController(private val orderUseCase: OrderUseCase) {

    @GetMapping("/{customerId}/orders")
    @Operation(summary = "List customer orders")
    fun getCustomerOrders(
        @PathVariable customerId: UUID,
        @RequestParam(required = false) status: List<OrderStatus>?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "0") offset: Int
    ): ResponseEntity<OrderListResponse> {
        val (orders, total) = orderUseCase.getCustomerOrders(customerId, status, limit, offset)
        val response = OrderListResponse(
            orders = orders.map { it.toResponse() },
            total = total
        )
        return ResponseEntity.ok(response)
    }
}
