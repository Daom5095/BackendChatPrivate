package com.chatprivate.auth;

import com.chatprivate.user.User;
import com.chatprivate.user.UserDto;
import com.chatprivate.user.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserRepository userRepository;

    public UserController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/me")
    public ResponseEntity<String> getMe(Authentication authentication) {
        return ResponseEntity.ok("Usuario autenticado: " + authentication.getName());
    }

    @GetMapping
    public ResponseEntity<List<UserDto>> getAllUsers(Authentication authentication) {
        String currentUsername = authentication.getName();
        List<UserDto> users = userRepository.findAll()
                .stream()
                // Filtramos para no incluir al usuario actual en la lista
                .filter(user -> !user.getUsername().equals(currentUsername))
                .map(user -> new UserDto(user.getId(), user.getUsername()))
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }
}