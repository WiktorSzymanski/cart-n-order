package pl.szymanski.wiktor.cart_n_order.domain.exception

import pl.szymanski.wiktor.cart_n_order.domain.model.OrderStatus
import java.util.UUID

class InsufficientStockException(val productId: UUID, val requested: Int, val available: Int) :
    RuntimeException("Insufficient stock for product $productId: requested=$requested, available=$available")

class OrderNotFoundException(val orderId: UUID) :
    RuntimeException("Order not found: $orderId")

class CartNotFoundException(val customerId: UUID) :
    RuntimeException("Cart not found for customer: $customerId")

class InvalidOrderTransitionException(val from: OrderStatus, val to: OrderStatus) :
    RuntimeException("Invalid order status transition: $from -> $to")

class CartEmptyException(val customerId: UUID) :
    RuntimeException("Cart is empty for customer: $customerId")

class ProductNotFoundException(val productId: UUID) :
    RuntimeException("Product not found: $productId")

class OptimisticConcurrencyException(val aggregateId: UUID, val conflictingVersion: Long, cause: Throwable? = null) :
    RuntimeException("Optimistic concurrency conflict for aggregate $aggregateId at version $conflictingVersion", cause)
