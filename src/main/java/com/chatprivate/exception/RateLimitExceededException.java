package com.chatprivate.exception;

/**
 * Excepción lanzada cuando un usuario excede el límite de peticiones permitidas.
 *
 * Esta excepción será manejada por el GlobalExceptionHandler y convertida
 * en una respuesta HTTP 429 (Too Many Requests).
 */
public class RateLimitExceededException extends RuntimeException {

    /**
     * Constructor con mensaje personalizado.
     *
     * @param message Mensaje descriptivo del error
     */
    public RateLimitExceededException(String message) {
        super(message);
    }

    /**
     * Constructor con mensaje y causa.
     *
     * @param message Mensaje descriptivo
     * @param cause Causa original de la excepción
     */
    public RateLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}