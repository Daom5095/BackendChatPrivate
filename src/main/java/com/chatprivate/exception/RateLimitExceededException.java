package com.chatprivate.exception;

/** Esta excepción avisa que se superó el límite de intentos permitido. */
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