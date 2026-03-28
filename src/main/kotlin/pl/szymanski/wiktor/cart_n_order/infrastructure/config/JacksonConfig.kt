package pl.szymanski.wiktor.cart_n_order.infrastructure.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import pl.szymanski.wiktor.cart_n_order.infrastructure.adapter.outbound.persistence.mapper.CartMapper
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

@Configuration
class JacksonConfig {

    @Bean
    fun objectMapper(): ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().build())
        .build()

    @Bean
    fun cartMapper(objectMapper: ObjectMapper) = CartMapper(objectMapper)
}
