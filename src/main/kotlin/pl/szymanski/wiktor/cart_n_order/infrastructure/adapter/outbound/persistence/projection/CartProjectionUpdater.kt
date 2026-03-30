package pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.projection

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Component
import pl.szymanski.wiktor.cart_n_order.domain.model.Cart
import tools.jackson.databind.ObjectMapper
import java.util.UUID

@Component
class CartProjectionUpdater(
    private val jdbc: NamedParameterJdbcTemplate,
    private val objectMapper: ObjectMapper
) {

    fun update(cart: Cart) {
        val itemsJson = objectMapper.writeValueAsString(
            cart.items.map { mapOf("productId" to it.productId, "quantity" to it.quantity, "unitPrice" to it.unitPrice) }
        )
        jdbc.update(
            """
            INSERT INTO proj_carts (customer_id, items, updated_at)
            VALUES (:customerId, CAST(:items AS jsonb), :updatedAt)
            ON CONFLICT (customer_id)
            DO UPDATE SET items = excluded.items, updated_at = excluded.updated_at
            """,
            mapOf(
                "customerId" to cart.customerId,
                "items" to itemsJson,
                "updatedAt" to java.sql.Timestamp.from(cart.updatedAt)
            )
        )
    }

    fun onCleared(customerId: UUID) {
        jdbc.update(
            "DELETE FROM proj_carts WHERE customer_id = :id",
            mapOf("id" to customerId)
        )
    }
}
