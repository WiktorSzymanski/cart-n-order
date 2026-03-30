package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.event.CartEvent
import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import pl.szymanski.wiktor.cart_n_order.domain.model.CartSnapshot
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.CartRepository
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.EventStore
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.StoredEvent
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.projection.CartProjectionUpdater
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class CartRepositoryAdapter(
    private val eventStore: EventStore,
    private val cartProjectionUpdater: CartProjectionUpdater,
    private val objectMapper: ObjectMapper
) : CartRepository {

    companion object {
        const val AGGREGATE_TYPE = "Cart"
        const val SNAPSHOT_THRESHOLD = 50
    }

    override fun findByCustomerId(customerId: UUID): Cart? {
        val snapshotRecord = eventStore.loadSnapshot(customerId, AGGREGATE_TYPE)
        val afterVersion = snapshotRecord?.version ?: 0L
        val storedEvents = eventStore.loadEvents(customerId, afterVersion)

        if (snapshotRecord == null && storedEvents.isEmpty()) return null

        val cartEvents = storedEvents.map { deserialize(it) }
        val snapshot = snapshotRecord?.let { objectMapper.readValue(it.payload, CartSnapshot::class.java) }
        return Cart.reconstitute(cartEvents, snapshot)
    }

    override fun save(cart: Cart): Cart {
        val uncommitted = cart.uncommittedEvents
        if (uncommitted.isEmpty()) return cart

        val expectedVersion = cart.version - uncommitted.size
        eventStore.appendEvents(cart.customerId, AGGREGATE_TYPE, uncommitted, expectedVersion)

        if (cart.items.isEmpty()) {
            cartProjectionUpdater.onCleared(cart.customerId)
        } else {
            cartProjectionUpdater.update(cart)
        }

        if (cart.version % SNAPSHOT_THRESHOLD == 0L) {
            val snapshotPayload = objectMapper.writeValueAsString(
                CartSnapshot(cart.customerId, cart.items, cart.updatedAt, cart.version)
            )
            eventStore.saveSnapshot(cart.customerId, AGGREGATE_TYPE, cart.version, snapshotPayload)
        }

        cart.markCommitted()
        return cart
    }

    override fun deleteByCustomerId(customerId: UUID) {
        val snapshotRecord = eventStore.loadSnapshot(customerId, AGGREGATE_TYPE)
        val afterVersion = snapshotRecord?.version ?: 0L
        val storedEvents = eventStore.loadEvents(customerId, afterVersion)
        val currentVersion = (snapshotRecord?.version ?: 0L) + storedEvents.size

        if (currentVersion > 0L) {
            val clearEvent = CartEvent.CartCleared(customerId)
            eventStore.appendEvents(customerId, AGGREGATE_TYPE, listOf(clearEvent), currentVersion)
        }
        cartProjectionUpdater.onCleared(customerId)
    }

    private fun deserialize(stored: StoredEvent): CartEvent = when (stored.eventType) {
        "ItemAddedOrUpdated" -> objectMapper.readValue(stored.payload, CartEvent.ItemAddedOrUpdated::class.java)
        "ItemRemoved" -> objectMapper.readValue(stored.payload, CartEvent.ItemRemoved::class.java)
        "CartCleared" -> objectMapper.readValue(stored.payload, CartEvent.CartCleared::class.java)
        else -> throw IllegalStateException("Unknown cart event type: ${stored.eventType}")
    }
}
