package com.chatprivate.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.user.DefaultUserDestinationResolver;
import org.springframework.messaging.simp.user.SimpUserRegistry;


import java.security.Principal;

public class CustomUserDestinationResolver extends DefaultUserDestinationResolver {

    private final SimpUserRegistry userRegistry;

    public CustomUserDestinationResolver(SimpUserRegistry userRegistry) {
        super(userRegistry);
        this.userRegistry = userRegistry;
        // Establecer el prefijo que usamos (/user)
        super.setUserDestinationPrefix("/user");
    }

    @Override
    public DestinationInfo resolveDestination(Message<?> message) {
        // Usa la lógica por defecto para resolver el destino
        DestinationInfo info = super.resolveDestination(message);

        // Intenta obtener el Principal de la cabecera del mensaje
        Principal principal = SimpMessageHeaderAccessor.getUser(message.getHeaders());

        if (principal != null) {
            // Log para ver qué nombre de usuario se está usando para buscar la sesión
            System.out.println("Resolviendo destino para usuario: " + principal.getName() + ", Destino Original: " + info.getSubscribeDestination());
        } else {
            System.out.println("No se encontró Principal al resolver destino para: " + info.getSubscribeDestination());
        }

        // Devolver la información resuelta por la clase padre
        return info;
    }
}