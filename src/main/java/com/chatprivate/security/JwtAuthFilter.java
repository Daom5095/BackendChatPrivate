package com.chatprivate.security;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Mi filtro personalizado que se ejecuta UNA VEZ por cada petición HTTP.
 * Su trabajo es interceptar todas las peticiones, buscar el token JWT
 * en la cabecera 'Authorization' y, si es válido, establecer
 * la autenticación en el contexto de seguridad de Spring.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserDetailsService userDetailsService; // Mi CustomUserDetailsService

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        // 1. Obtener la cabecera 'Authorization'
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        final String username;

        // 2. Si no hay cabecera o no empieza con "Bearer ",
        // no hago nada y paso la petición al siguiente filtro.
        // (Probablemente sea una petición a un endpoint público como /api/auth/login)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. Extraigo el token (quitando "Bearer ")
        jwt = authHeader.substring(7);

        // 4. Extraigo el username del token
        username = jwtService.extractUsername(jwt);

        // 5. Si tengo username y NO hay ya una autenticación en el contexto
        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {

            // Cargo el usuario desde la BD
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            // 6. Valido el token (compruebo firma, expiración y que coincida el usuario)
            if (jwtService.isTokenValid(jwt, userDetails)) {

                // 7. Si es válido, creo el token de autenticación de Spring
                UsernamePasswordAuthenticationToken authToken =
                        new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                // 8. ¡Clave! Establezco la autenticación en el contexto.
                // A partir de aquí, Spring considera al usuario como "logueado"
                // para esta petición.
                SecurityContextHolder.getContext().setAuthentication(authToken);
            }
        }

        // 9. Paso la petición al siguiente filtro en la cadena.
        filterChain.doFilter(request, response);
    }
}