package com.chatprivate.exception;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

/**
 * Mi "Controlador de Errores" global.
 * La anotación @RestControllerAdvice le dice a Spring: "Si cualquier
 * @RestController lanza una excepción, búsca un manejador aquí primero".
 */
@RestControllerAdvice
@Slf4j // Uso un logger para registrar los errores internos
public class GlobalExceptionHandler {

    /**
     * Maneja errores de validación (Punto 2).
     * Se activa cuando un DTO anotado con @Valid falla la validación.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationExceptions(MethodArgumentNotValidException ex, HttpServletRequest request) {
        // Combino todos los mensajes de error de los campos en un solo string
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("Error de validación: {}", errorMessage);
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                errorMessage,
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja errores de argumentos ilegales (ej. "El mapa de claves no puede estar vacío").
     * Devuelve un 400 Bad Request.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(IllegalArgumentException ex, HttpServletRequest request) {
        log.warn("Argumento ilegal: {}", ex.getMessage());
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(), // El mensaje que yo mismo definí en el servicio
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja credenciales incorrectas en el login.
     * Devuelve un 401 Unauthorized.
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentialsException(BadCredentialsException ex, HttpServletRequest request) {
        log.warn("Intento de login fallido: {}", ex.getMessage());
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                "Usuario o contraseña incorrectos", // Mensaje genérico por seguridad
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Maneja el caso en que un usuario no se encuentra (login o carga de seguridad).
     * Devuelve un 404 Not Found.
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUsernameNotFoundException(UsernameNotFoundException ex, HttpServletRequest request) {
        log.warn("Usuario no encontrado: {}", ex.getMessage());
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.NOT_FOUND.value(),
                "Usuario o contraseña incorrectos", // Mensaje genérico
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja intentos de acceso denegado (ej. no-owner intentando borrar a alguien).
     * Devuelve un 403 Forbidden.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(AccessDeniedException ex, HttpServletRequest request) {
        log.warn("Acceso denegado: {}", ex.getMessage());
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(), // "No autorizado para eliminar..."
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.FORBIDDEN);
    }


    /**
     * Mi "catch-all". Si ocurre cualquier otra excepción que no manejé
     * (ej. NullPointerException), esto la atrapará.
     * Devuelve un 500 Internal Server Error.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(Exception ex, HttpServletRequest request) {
        // Este es un error inesperado, así que lo logueo como ERROR
        log.error("Error interno inesperado: {}", ex.getMessage(), ex);
        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocurrió un error inesperado. Por favor, intenta de nuevo.",
                request.getRequestURI()
        );
        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}