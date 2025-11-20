package com.chatprivate.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración manual de Flyway.
 *
 * PROPÓSITO:
 * Asegurar que Flyway haga baseline automáticamente cuando encuentra
 * una BD que ya tiene datos pero sin tabla de historial de Flyway.
 *
 * CUÁNDO USAR ESTE BEAN:
 * Solo si la configuración en application.yml no funciona.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            // Intenta hacer baseline si es necesario
            // repair() arregla la tabla de historial si está corrupta
            flyway.repair();

            // migrate() ejecuta las migraciones pendientes
            flyway.migrate();
        };
    }
}