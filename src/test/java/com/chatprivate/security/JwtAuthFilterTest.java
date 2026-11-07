package com.chatprivate.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.FilterChain;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Aquí reviso qué hace el filtro cuando llega una petición con o sin JWT. */
class JwtAuthFilterTest {

    private final FakeJwtService jwtService = new FakeJwtService();
    private final FakeUserDetailsService userDetailsService = new FakeUserDetailsService();
    private final JwtAuthFilter filter = new JwtAuthFilter();

    JwtAuthFilterTest() {
        ReflectionTestUtils.setField(filter, "jwtService", jwtService);
        ReflectionTestUtils.setField(filter, "userDetailsService", userDetailsService);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    // Si no llega la cabecera, dejo que la petición siga sin autenticación.
    @Test
    void doFilter_ShouldContinue_WhenAuthorizationHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, response, chainCounting(calls));

        assertEquals(1, calls.get());
        assertEquals(0, userDetailsService.calls);
    }

    // Si la cabecera no usa Bearer, tampoco intento leer un JWT.
    @Test
    void doFilter_ShouldContinue_WhenAuthorizationHeaderIsNotBearer() throws Exception {
        MockHttpServletRequest request = requestWithToken("Basic abc123");
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(request, new MockHttpServletResponse(), chainCounting(calls));

        assertEquals(1, calls.get());
        assertEquals(0, userDetailsService.calls);
    }

    // Si el JWT no trae un usuario, no busco ninguna cuenta.
    @Test
    void doFilter_ShouldNotLoadUser_WhenTokenHasNoUsername() throws Exception {
        jwtService.username = null;
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(requestWithToken("Bearer token"), new MockHttpServletResponse(), chainCounting(calls));

        assertEquals(1, calls.get());
        assertEquals(0, userDetailsService.calls);
    }

    // Si la firma del JWT es válida, dejo al usuario autenticado en el contexto.
    @Test
    void doFilter_ShouldAuthenticateUser_WhenTokenIsValid() throws Exception {
        UserDetails user = userDetailsService.user;
        jwtService.username = "diego";
        jwtService.valid = true;
        AtomicInteger calls = new AtomicInteger();

        filter.doFilter(requestWithToken("Bearer token"), new MockHttpServletResponse(), chainCounting(calls));

        assertEquals(1, calls.get());
        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals("diego", SecurityContextHolder.getContext().getAuthentication().getName());
    }

    // Si el JWT no pasa la validación, no autentico al usuario.
    @Test
    void doFilter_ShouldNotAuthenticateUser_WhenTokenIsInvalid() throws Exception {
        jwtService.username = "diego";
        jwtService.valid = false;

        filter.doFilter(requestWithToken("Bearer token"), new MockHttpServletResponse(), (req, res) -> {});

        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    // Si Spring ya autenticó la petición, no vuelvo a consultar el usuario.
    @Test
    void doFilter_ShouldKeepExistingAuthentication() throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("already-authenticated", null, AuthorityUtils.NO_AUTHORITIES));
        jwtService.username = "diego";

        filter.doFilter(requestWithToken("Bearer token"), new MockHttpServletResponse(), (req, res) -> {});

        assertEquals("already-authenticated", SecurityContextHolder.getContext().getAuthentication().getName());
        assertEquals(0, userDetailsService.calls);
    }

    private MockHttpServletRequest requestWithToken(String authorization) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", authorization);
        return request;
    }

    private FilterChain chainCounting(AtomicInteger calls) {
        return (request, response) -> calls.incrementAndGet();
    }

    private static class FakeJwtService extends JwtService {
        private String username;
        private boolean valid;

        @Override
        public String extractUsername(String token) {
            return username;
        }

        @Override
        public boolean isTokenValid(String token, UserDetails userDetails) {
            return valid;
        }
    }

    private static class FakeUserDetailsService implements UserDetailsService {
        private final UserDetails user = org.springframework.security.core.userdetails.User
                .withUsername("diego").password("hash").authorities("ROLE_USER").build();
        private int calls;

        @Override
        public UserDetails loadUserByUsername(String username) {
            calls++;
            return user;
        }
    }
}
