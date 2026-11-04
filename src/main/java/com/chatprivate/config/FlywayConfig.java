package com.chatprivate.config;

import org.springframework.boot.flyway.autoconfigure.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Esta configuración prepara las migraciones de la base de datos al iniciar la aplicación. */
@Configuration
public class FlywayConfig {

    // Reviso el historial de Flyway y aplico las migraciones pendientes al iniciar.
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
