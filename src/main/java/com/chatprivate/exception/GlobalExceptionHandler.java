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

/** Aquí manejo los errores de la API para responder de forma ordenada. */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Maneja errores de validación de DTOs (@Valid).
     * Devuelve HTTP 400 (Bad Request).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDto> handleValidationExceptions(
            MethodArgumentNotValidException ex,
            HttpServletRequest request) {

        // Combino todos los mensajes de error en uno solo
        String errorMessage = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn(" Error de validación en {}: {}", request.getRequestURI(), errorMessage);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                errorMessage,
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja argumentos ilegales (errores de lógica de negocio).
     * Devuelve HTTP 400 (Bad Request).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponseDto> handleIllegalArgumentException(
            IllegalArgumentException ex,
            HttpServletRequest request) {

        log.warn("⚠️ Argumento ilegal en {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.BAD_REQUEST.value(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.BAD_REQUEST);
    }

    /**
     * Maneja credenciales incorrectas en el login.
     * Devuelve HTTP 401 (Unauthorized).
     */
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErrorResponseDto> handleBadCredentialsException(
            BadCredentialsException ex,
            HttpServletRequest request) {

        log.warn("Intento de login fallido en {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.UNAUTHORIZED.value(),
                "Usuario o contraseña incorrectos",
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.UNAUTHORIZED);
    }

    /**
     * Maneja usuarios no encontrados.
     * Devuelve HTTP 404 (Not Found).
     */
    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ErrorResponseDto> handleUsernameNotFoundException(
            UsernameNotFoundException ex,
            HttpServletRequest request) {

        log.warn("👤 Usuario no encontrado en {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.NOT_FOUND.value(),
                "Usuario o contraseña incorrectos", // Mensaje genérico por seguridad
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.NOT_FOUND);
    }

    /**
     * Maneja accesos denegados (violaciones de permisos).
     * Devuelve HTTP 403 (Forbidden).
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponseDto> handleAccessDeniedException(
            AccessDeniedException ex,
            HttpServletRequest request) {

        // Este es un evento de seguridad importante, lo logueo como WARNING
        log.warn("Acceso denegado en {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.FORBIDDEN.value(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.FORBIDDEN);
    }

    /**
     * Maneja rate limit excedido (demasiadas peticiones).
     * Devuelve HTTP 429 (Too Many Requests).
     *
     * ¡NUEVO EN SESIÓN 2!
     */
    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ErrorResponseDto> handleRateLimitExceededException(
            RateLimitExceededException ex,
            HttpServletRequest request) {

        // Este es un evento de seguridad crítico (posible ataque)
        log.warn("RATE LIMIT EXCEDIDO en {}: {}", request.getRequestURI(), ex.getMessage());

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                ex.getMessage(),
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.TOO_MANY_REQUESTS);
    }

    /**
     * Catch-all: Maneja cualquier excepción no prevista.
     * Devuelve HTTP 500 (Internal Server Error).
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDto> handleGlobalException(
            Exception ex,
            HttpServletRequest request) {

        // Este es un error inesperado, lo logueo como ERROR con el stack trace completo
        log.error("Error interno inesperado en {}: {}",
                request.getRequestURI(), ex.getMessage(), ex);

        ErrorResponseDto errorDto = new ErrorResponseDto(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "Ocurrió un error inesperado. Por favor, intenta de nuevo más tarde.",
                request.getRequestURI()
        );

        return new ResponseEntity<>(errorDto, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}