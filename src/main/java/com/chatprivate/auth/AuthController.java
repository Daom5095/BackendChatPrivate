package com.chatprivate.auth;


import com.chatprivate.user.UserService;
import lombok.RequiredArgsConstructor; // Importar
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor // Genera un constructor con todos los campos 'final'
public class AuthController {

    // Se pone 'final' para que Lombok lo detecte
    private final UserService userService;

    // El constructor manual se elimina (Lombok lo crea)

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(userService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(userService.login(request));
    }
}