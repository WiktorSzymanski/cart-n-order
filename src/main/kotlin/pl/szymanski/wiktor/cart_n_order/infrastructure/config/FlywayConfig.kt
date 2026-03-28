package pl.szymanski.wiktor.cart_n_order.infrastructure.config

import org.flywaydb.core.Flyway
import org.springframework.boot.jpa.autoconfigure.EntityManagerFactoryDependsOnPostProcessor
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import javax.sql.DataSource

@Configuration
class FlywayConfig {

    @Bean(initMethod = "migrate")
    fun flyway(dataSource: DataSource): Flyway = Flyway.configure()
        .dataSource(dataSource)
        .locations("classpath:db/migration")
        .load()

    @Bean
    fun flywayEntityManagerFactoryInitializer(): EntityManagerFactoryDependsOnPostProcessor =
        EntityManagerFactoryDependsOnPostProcessor("flyway")
}
