package pl.szymanski.wiktor.cart_n_order.domain.port.outbound

import pl.szymanski.wiktor.cart_n_order.domain.model.DomainEvent
import java.util.UUID

interface EventStore {
    /**
     * Appends events for an aggregate. [expectedVersion] is the version the aggregate had before
     * these events — i.e., the sequence_number of the last persisted event (0 if new aggregate).
     * Throws [pl.szymanski.wiktor.cart_n_order.domain.exception.OptimisticConcurrencyException]
     * if another transaction has already written at that version.
     */
    fun appendEvents(
        aggregateId: UUID,
        aggregateType: String,
        events: List<DomainEvent>,
        expectedVersion: Long
    )

    /** Returns all events after [afterVersion] in ascending sequence order. */
    fun loadEvents(aggregateId: UUID, afterVersion: Long = 0L): List<StoredEvent>

    fun saveSnapshot(aggregateId: UUID, aggregateType: String, version: Long, payload: String)
    fun loadSnapshot(aggregateId: UUID, aggregateType: String): SnapshotRecord?
}

data class StoredEvent(val sequenceNumber: Long, val eventType: String, val payload: String)
data class SnapshotRecord(val version: Long, val payload: String)
