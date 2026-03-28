package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest

import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import pl.szymanski.wiktor.cart_n_order.domain.exception.CartEmptyException
import pl.szymanski.wiktor.cart_n_order.domain.exception.CartNotFoundException
import pl.szymanski.wiktor.cart_n_order.domain.exception.InsufficientStockException
import pl.szymanski.wiktor.cart_n_order.domain.exception.InvalidOrderTransitionException
import pl.szymanski.wiktor.cart_n_order.domain.exception.OrderNotFoundException
import pl.szymanski.wiktor.cart_n_order.domain.exception.ProductNotFoundException
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.inbound.rest.dto.ErrorResponse

@RestControllerAdvice
class GlobalExceptionHandler {

    @ExceptionHandler(InsufficientStockException::class)
    fun handleInsufficientStock(ex: InsufficientStockException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT)
            .body(ErrorResponse(ex.message ?: "Insufficient stock", "INSUFFICIENT_STOCK"))

    @ExceptionHandler(OrderNotFoundException::class)
    fun handleOrderNotFound(ex: OrderNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Order not found", "ORDER_NOT_FOUND"))

    @ExceptionHandler(CartNotFoundException::class)
    fun handleCartNotFound(ex: CartNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Cart not found", "CART_NOT_FOUND"))

    @ExceptionHandler(ProductNotFoundException::class)
    fun handleProductNotFound(ex: ProductNotFoundException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.NOT_FOUND)
            .body(ErrorResponse(ex.message ?: "Product not found", "PRODUCT_NOT_FOUND"))

    @ExceptionHandler(InvalidOrderTransitionException::class)
    fun handleInvalidTransition(ex: InvalidOrderTransitionException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Invalid order transition", "INVALID_ORDER_TRANSITION"))

    @ExceptionHandler(CartEmptyException::class)
    fun handleCartEmpty(ex: CartEmptyException): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(ex.message ?: "Cart is empty", "CART_EMPTY"))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> {
        val message = ex.bindingResult.fieldErrors
            .joinToString("; ") { "${it.field}: ${it.defaultMessage}" }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
            .body(ErrorResponse(message, "VALIDATION_ERROR"))
    }
}
