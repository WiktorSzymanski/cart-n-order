package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.context.annotation.Profile
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import pl.szymanski.wiktor.cart_n_order.domain.port.inbound.AdminUseCase
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateProductRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.CreateProductResponse
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.GenerateCustomersRequest
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.GenerateCustomersResponse

@RestController
@RequestMapping("/admin")
@Profile("!prod")
@Tag(name = "Admin", description = "Admin / data seeding endpoints (disabled in prod)")
class AdminController(private val adminUseCase: AdminUseCase) {

    @PostMapping("/products")
    @Operation(summary = "Create a product with initial stock")
    fun createProduct(
        @Valid @RequestBody request: CreateProductRequest
    ): ResponseEntity<CreateProductResponse> {
        val product = adminUseCase.createProduct(request.name, request.initialStock)
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(CreateProductResponse(product.id, product.name))
    }

    @PostMapping("/customers")
    @Operation(summary = "Generate test customer UUIDs")
    fun generateCustomers(
        @Valid @RequestBody request: GenerateCustomersRequest
    ): ResponseEntity<GenerateCustomersResponse> {
        val ids = adminUseCase.generateCustomers(request.count)
        return ResponseEntity.status(HttpStatus.CREATED).body(GenerateCustomersResponse(ids))
    }
}
