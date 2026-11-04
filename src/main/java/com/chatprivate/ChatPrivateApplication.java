package com.chatprivate;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Esta es la clase que pone en marcha el backend de ChatPrivate. */
@SpringBootApplication
public class ChatPrivateApplication {

    // Arranco Spring Boot para que se carguen las rutas y servicios del backend.
    public static void main(String[] args) {
        SpringApplication.run(ChatPrivateApplication.class, args);
    }

}