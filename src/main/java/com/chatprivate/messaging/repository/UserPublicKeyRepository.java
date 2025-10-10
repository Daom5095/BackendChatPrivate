package com.chatprivate.messaging.repository;

import com.chatprivate.messaging.model.UserPublicKey;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface UserPublicKeyRepository extends JpaRepository<UserPublicKey, Long> {
    Optional<UserPublicKey> findByUserId(Long userId);
}
