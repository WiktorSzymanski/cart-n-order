package pl.szymanski.wiktor.cart_n_order.domain.model

import pl.szymanski.wiktor.cart_n_order.domain.event.CartEvent
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class Cart private constructor(val customerId: UUID) : AggregateRoot<CartEvent>() {

    private var _items: List<CartItem> = emptyList()
    private var _updatedAt: Instant = Instant.now()

    val items: List<CartItem> get() = _items
    val updatedAt: Instant get() = _updatedAt
    val totalItems: Int get() = _items.sumOf { it.quantity }
    val totalAmount: BigDecimal get() = _items.sumOf { it.unitPrice * it.quantity.toBigDecimal() }

    fun addOrUpdateItem(productId: UUID, quantity: Int, unitPrice: BigDecimal): Cart {
        raise(CartEvent.ItemAddedOrUpdated(customerId, productId, quantity, unitPrice))
        return this
    }

    fun removeItem(productId: UUID, quantity: Int?): Cart {
        if (_items.none { it.productId == productId }) return this
        raise(CartEvent.ItemRemoved(customerId, productId, quantity))
        return this
    }

    override fun applyEvent(event: CartEvent) {
        when (event) {
            is CartEvent.ItemAddedOrUpdated -> {
                val existing = _items.find { it.productId == event.productId }
                _items = if (existing != null) {
                    _items.map {
                        if (it.productId == event.productId) it.copy(quantity = it.quantity + event.quantity)
                        else it
                    }
                } else {
                    _items + CartItem(event.productId, event.quantity, event.unitPrice)
                }
                _updatedAt = event.occurredAt
            }
            is CartEvent.ItemRemoved -> {
                val existing = _items.find { it.productId == event.productId }
                if (existing != null) {
                    _items = if (event.quantity == null || existing.quantity <= event.quantity) {
                        _items.filter { it.productId != event.productId }
                    } else {
                        _items.map {
                            if (it.productId == event.productId) it.copy(quantity = it.quantity - event.quantity)
                            else it
                        }
                    }
                    _updatedAt = event.occurredAt
                }
            }
            is CartEvent.CartCleared -> {
                _items = emptyList()
                _updatedAt = event.occurredAt
            }
        }
    }

    companion object {
        fun create(customerId: UUID): Cart = Cart(customerId)

        fun reconstitute(events: List<CartEvent>, snapshot: CartSnapshot?): Cart {
            val cart = if (snapshot != null) {
                Cart(snapshot.customerId).also { c ->
                    c._items = snapshot.items
                    c._updatedAt = snapshot.updatedAt
                    c.restoreVersion(snapshot.version)
                }
            } else {
                val customerId = when (val first = events.first()) {
                    is CartEvent.ItemAddedOrUpdated -> first.customerId
                    is CartEvent.ItemRemoved -> first.customerId
                    is CartEvent.CartCleared -> first.customerId
                }
                Cart(customerId)
            }
            events.forEach { cart.replay(it) }
            return cart
        }
    }
}
