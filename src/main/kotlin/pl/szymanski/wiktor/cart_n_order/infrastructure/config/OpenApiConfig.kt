package pl.szymanski.wiktor.cart_n_order.infrastructure.config

import io.swagger.v3.oas.annotations.OpenAPIDefinition
import io.swagger.v3.oas.annotations.info.Info
import org.springframework.context.annotation.Configuration

@Configuration
@OpenAPIDefinition(
    info = Info(
        title = "Cart & Order API",
        version = "1.0",
        description = "Cart → Order → Inventory management system"
    )
)
class OpenApiConfig
