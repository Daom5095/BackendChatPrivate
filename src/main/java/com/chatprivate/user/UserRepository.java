package com.chatprivate.user;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Este repositorio permite buscar y guardar usuarios en la base de datos. */
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);
}