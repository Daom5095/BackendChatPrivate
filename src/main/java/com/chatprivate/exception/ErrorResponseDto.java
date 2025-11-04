package com.chatprivate.exception;

import java.time.Instant;

/**
 * Este es mi DTO estándar para devolver errores de API.
 * Cada vez que algo falle, el frontend recibirá un JSON con esta estructura.
 */
public class ErrorResponseDto {

    private Instant timestamp; // La hora exacta del error
    private int status; // El código HTTP (ej. 400, 404, 500)
    private String error; // El mensaje de error que puedo mostrar
    private String path; // La URL que intentó llamar el frontend

    public ErrorResponseDto(int status, String error, String path) {
        this.timestamp = Instant.now();
        this.status = status;
        this.error = error;
        this.path = path;
    }


    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public int getStatus() {
        return status;
    }

    public void setStatus(int status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }
}