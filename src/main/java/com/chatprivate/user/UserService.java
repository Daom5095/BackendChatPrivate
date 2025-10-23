package com.chatprivate.user;

import com.chatprivate.auth.AuthResponse;
import com.chatprivate.auth.LoginRequest;
import com.chatprivate.auth.RegisterRequest;
import com.chatprivate.security.JwtService;
import lombok.RequiredArgsConstructor; // Importar
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import com.chatprivate.messaging.model.UserPublicKey;
import com.chatprivate.messaging.repository.UserPublicKeyRepository;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor // Genera el constructor
public class UserService {

    // Convertidos a 'final'
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UserPublicKeyRepository userPublicKeyRepository;

    // El constructor manual se elimina (Lombok lo crea)

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        // ... (el resto del método sigue igual)
        User user = User.builder()
                .username(request.getUsername())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .build();

        User savedUser = userRepository.save(user);

        if (request.getPublicKey() != null && !request.getPublicKey().isEmpty()) {
            UserPublicKey upk = new UserPublicKey();
            upk.setUserId(savedUser.getId());
            upk.setPublicKeyPem(request.getPublicKey());
            userPublicKeyRepository.save(upk);
        }

        String token = jwtService.generateToken(savedUser);
        return AuthResponse.builder()
                .token(token)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        // ... (el resto del método sigue igual)
        User user = userRepository.findByUsername(request.getUsername())
                .orElseThrow(() -> new UsernameNotFoundException("No existe usuario"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new BadCredentialsException("Contraseña incorrecta");
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .build();
    }
}