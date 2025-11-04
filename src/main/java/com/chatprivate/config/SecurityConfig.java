package com.chatprivate.config;

import com.chatprivate.security.JwtAuthFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
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

import java.util.*;
import java.util.stream.Collectors;

/**
 * Configuración central de Spring Security.
 * Aquí defino cómo se maneja la seguridad de mi API,
 * qué rutas son públicas, cuáles son privadas y cómo se validan los tokens.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final UserDetailsService userDetailsService;
    private final Environment env; // Para leer `application.yml`

    public SecurityConfig(JwtAuthFilter jwtAuthFilter,
                          UserDetailsService userDetailsService,
                          Environment env) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.userDetailsService = userDetailsService;
        this.env = env;
    }

    /**
     * Defino el Bean para hashear contraseñas.
     * Uso BCrypt que es el estándar y muy seguro.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Defino el "proveedor" de autenticación.
     * Le digo a Spring Security: "Usa mi CustomUserDetailsService para
     * buscar usuarios y usa mi PasswordEncoder para comparar contraseñas".
     */
    @Bean
    public DaoAuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider();
        authProvider.setUserDetailsService(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }

    /**
     * Expongo el AuthenticationManager como un Bean.
     * Lo necesitaré en mi UserService (aunque ahora lo uso implícitamente).
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Este es el Bean más importante. Define la cadena de filtros de seguridad.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // 1. Configurar CORS usando mi bean `corsConfigurationSource`
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                // 2. Desactivar CSRF (Cross-Site Request Forgery).
                // Es seguro hacerlo porque uso JWT (que no se basa en cookies de sesión)
                // y mi API es 'stateless'.
                .csrf(csrf -> csrf.disable())

                // 3. Establecer la política de sesión como STATELESS.
                // Le digo a Spring que no cree sesiones HTTP, cada petición
                // debe traer su propio token (JWT).
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // 4. Definir las reglas de autorización.
                .authorizeHttpRequests(auth -> auth
                        // Permito el acceso público a mis endpoints de autenticación,
                        // a la documentación de la API (swagger) y al endpoint de WebSocket (/ws).
                        .requestMatchers("/api/auth/**", "/v3/api-docs/**", "/swagger-ui/**", "/ws/**").permitAll()
                        // Cualquier otra petición debe estar autenticada.
                        .anyRequest().authenticated()
                )

                // 5. Registrar mi proveedor de autenticación
                .authenticationProvider(authenticationProvider())

                // 6. ¡Clave! Añado mi filtro JwtAuthFilter ANTES del filtro
                // estándar de autenticación. Así valido el token en cada petición.
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * Configuración de CORS (Cross-Origin Resource Sharing).
     * Esto es vital para permitir que mi frontend (ej. localhost:52803)
     * pueda hacer peticiones a mi backend (ej. localhost:8080).
     */
    @Bean
    CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Intento leer los orígenes permitidos desde application.yml
        String raw = env.getProperty("app.cors.allowed-origins");
        List<String> allowedOrigins = parseAllowedOrigins(raw);

        // Si no se define ninguno en el YAML, pongo un fallback para desarrollo
        if (allowedOrigins.isEmpty()) {
            allowedOrigins = Arrays.asList("http://localhost:52803", "http://127.0.0.1:52803");
        }

        configuration.setAllowedOrigins(allowedOrigins);
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "X-Requested-With",
                "accept", "Origin", "Access-Control-Request-Method",
                "Access-Control-Request-Headers"
        ));
        configuration.setAllowCredentials(true); // Permitir que se envíen cookies/tokens

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration); // Aplico esta config a todas las rutas
        return source;
    }

    /**
     * Método helper para parsear la lista de orígenes desde el .yml,
     * ya sea que venga como "url1,url2" o "[url1, url2]".
     */
    private List<String> parseAllowedOrigins(String raw) {
        if (raw == null) {
            return Collections.emptyList();
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("[") && cleaned.endsWith("]")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        if (cleaned.isEmpty()) return Collections.emptyList();

        String[] parts = cleaned.split(",");
        return Arrays.stream(parts)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toList());
    }
}