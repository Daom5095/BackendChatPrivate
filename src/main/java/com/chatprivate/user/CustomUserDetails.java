package com.chatprivate.user;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import java.util.Collection;
import java.util.Collections;

public class CustomUserDetails implements UserDetails {

    private final User user;

    public CustomUserDetails(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }

    // --- AÑADIR ESTE MÉTODO ---
    /**
     * Sobrescribe el método toString() para que siempre devuelva el nombre de usuario.
     * Esto es crucial porque algunos componentes de Spring STOMP usan toString()
     * en lugar de getName() para identificar al 'Principal' de la sesión.
     * Esto resuelve la inconsistencia que causa el error "Could not find session id".
     */
    @Override
    public String toString() {
        return this.user.getUsername();
    }
    // --- FIN DE LA ADICIÓN ---

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptyList();
    }

    @Override
    public String getPassword() {
        return user.getPassword();
    }

    @Override
    public String getUsername() {
        return user.getUsername();
    }

    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}