package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence

import org.springframework.dao.DataIntegrityViolationException
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.exception.OptimisticConcurrencyException
import pl.szymanski.wiktor.cart_n_order.domain.model.DomainEvent
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.EventStore
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.SnapshotRecord
import pl.szymanski.wiktor.cart_n_order.domain.port.outbound.StoredEvent
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class EventStoreAdapter(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) : EventStore {

    override fun appendEvents(
        aggregateId: UUID,
        aggregateType: String,
        events: List<DomainEvent>,
        expectedVersion: Long
    ) {
        events.forEachIndexed { index, event ->
            val seqNum = expectedVersion + 1 + index
            try {
                jdbc.update(
                    """
                    INSERT INTO event_store
                        (id, aggregate_id, aggregate_type, sequence_number, event_type, payload, occurred_at)
                    VALUES
                        (:id, :aggregateId, :aggregateType, :seqNum, :eventType, CAST(:payload AS jsonb), NOW())
                    """,
                    mapOf(
                        "id" to UUID.randomUUID(),
                        "aggregateId" to aggregateId,
                        "aggregateType" to aggregateType,
                        "seqNum" to seqNum,
                        "eventType" to event::class.simpleName,
                        "payload" to objectMapper.writeValueAsString(event)
                    )
                )
            } catch (ex: DataIntegrityViolationException) {
                throw OptimisticConcurrencyException(aggregateId, seqNum, ex)
            }
        }
    }

    override fun loadEvents(aggregateId: UUID, afterVersion: Long): List<StoredEvent> =
        jdbc.query(
            """
            SELECT sequence_number, event_type, payload::text
            FROM event_store
            WHERE aggregate_id = :id AND sequence_number > :afterVersion
            ORDER BY sequence_number ASC
            """,
            mapOf("id" to aggregateId, "afterVersion" to afterVersion)
        ) { rs, _ ->
            StoredEvent(
                sequenceNumber = rs.getLong("sequence_number"),
                eventType = rs.getString("event_type"),
                payload = rs.getString("payload")
            )
        }

    override fun saveSnapshot(aggregateId: UUID, aggregateType: String, version: Long, payload: String) {
        jdbc.update(
            """
            INSERT INTO snapshots (aggregate_id, aggregate_type, version, payload)
            VALUES (:aggregateId, :aggregateType, :version, CAST(:payload AS jsonb))
            ON CONFLICT (aggregate_id, aggregate_type)
            DO UPDATE SET version = excluded.version, payload = excluded.payload, created_at = NOW()
            """,
            mapOf(
                "aggregateId" to aggregateId,
                "aggregateType" to aggregateType,
                "version" to version,
                "payload" to payload
            )
        )
    }

    override fun loadSnapshot(aggregateId: UUID, aggregateType: String): SnapshotRecord? =
        jdbc.query(
            """
            SELECT version, payload::text
            FROM snapshots
            WHERE aggregate_id = :id AND aggregate_type = :type
            """,
            mapOf("id" to aggregateId, "type" to aggregateType)
        ) { rs, _ ->
            SnapshotRecord(
                version = rs.getLong("version"),
                payload = rs.getString("payload")
            )
        }.firstOrNull()
}
