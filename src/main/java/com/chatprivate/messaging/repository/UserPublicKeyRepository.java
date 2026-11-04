package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.UserPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

/** Este repositorio permite buscar y guardar las claves públicas. */
public interface UserPublicKeyRepository extends JpaRepository<UserPublicKey, Long> {
    Optional<UserPublicKey> findByUserId(Long userId);
}
