package com.chatprivate.config;

import com.chatprivate.security.JwtAuthFilter;
// import org.springframework.beans.factory.annotation.Value; // ¡LÍNEA COMENTADA!
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;



import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import java.util.Arrays;
import java.util.List; // Importar List

/**
 * Configuración de seguridad
 */
@Configuration
@EnableWebSecurity
public class    SecurityConfig {


    // --- ¡CAMPO COMENTADO! ---
    // @Value("${app.cors.allowed-origins}")
    // private List<String> allowedOrigins;
    // --------------------------------------------------

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;

    // Constructor manual para inyectar los dos beans
    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsService userDetailsService) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
    }

    // 1) Password encoder
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // 2) Provider con UserDetailsService + PasswordEncoder
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        // Ya no usamos el constructor vacío, pasamos encoder directo para evitar warning
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(passwordEncoder());
        authProvider.setUserDetailsService(userDetailsService);
        return authProvider;
    }

    // 3) AuthenticationManager
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    // 4) Filtro principal
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // Añadimos la configuración de CORS aquí
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/ws/**").permitAll() // Permitimos también la ruta del WebSocket
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * ¡AQUÍ ESTÁ EL CAMBIO!
     * Usamos una lista "quemada" (hardcoded) en lugar del @Value
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // --- CONFIGURACIÓN "QUEMADA" (TEMPORAL) ---
        List<String> allowedOrigins = Arrays.asList(
                "http://localhost:52803",
                "http://128.0.0.1:52803" // Corregí 127.0.0.1 por si acaso
        );
        configuration.setAllowedOrigins(allowedOrigins);
        // ------------------------------------------

        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Cabeceras comunes + Authorization
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With",
                "accept", "Origin", "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        // Permitir credenciales (importante si usas cookies o auth headers)
        configuration.setAllowCredentials(true);
        // Exponer cabeceras si es necesario (normalmente no para GET/POST simples)
        // configuration.setExposedHeaders(Arrays.asList("..."));
        // --- FIN CONFIGURACIÓN EXPLÍCITA ---

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Aplicar a todas las rutas
        return source;
    }
}