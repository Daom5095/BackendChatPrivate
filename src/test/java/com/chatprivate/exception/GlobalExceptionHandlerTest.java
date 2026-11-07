package com.chatprivate.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.core.MethodParameter;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

/** Aquí compruebo que los errores se conviertan en respuestas HTTP entendibles. */
class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        // Uso una ruta conocida para revisar que también llegue en la respuesta.
        handler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/prueba");
    }

    @Test
    void handlesValidationErrorAsBadRequest() throws Exception {
        // Simulo el error que se genera cuando un campo no cumple su validación.
        Method method = getClass().getDeclaredMethod("validationTarget", String.class);
        MethodParameter parameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bindingResult =
                new BeanPropertyBindingResult(new Object(), "request");
        bindingResult.addError(new FieldError("request", "username", "es obligatorio"));
        MethodArgumentNotValidException exception =
                new MethodArgumentNotValidException(parameter, bindingResult);

        var response = handler.handleValidationExceptions(exception, request);

        assertErrorResponse(response.getBody(), HttpStatus.BAD_REQUEST, "username: es obligatorio");
    }

    @Test
    void handlesIllegalArgumentAsBadRequest() {
        // Compruebo que un argumento inválido regrese como HTTP 400.
        var response = handler.handleIllegalArgumentException(
                new IllegalArgumentException("El ID no es válido"), request);

        assertErrorResponse(response.getBody(), HttpStatus.BAD_REQUEST, "El ID no es válido");
    }

    @Test
    void handlesBadCredentialsAsUnauthorized() {
        // Compruebo que las credenciales incorrectas no revelen detalles adicionales.
        var response = handler.handleBadCredentialsException(
                new BadCredentialsException("detalle interno"), request);

        assertErrorResponse(response.getBody(), HttpStatus.UNAUTHORIZED,
                "Usuario o contraseña incorrectos");
    }

    @Test
    void handlesMissingUserAsNotFoundWithGenericMessage() {
        // Compruebo que no se indique si el usuario existe o no durante el login.
        var response = handler.handleUsernameNotFoundException(
                new UsernameNotFoundException("cuenta inexistente"), request);

        assertErrorResponse(response.getBody(), HttpStatus.NOT_FOUND,
                "Usuario o contraseña incorrectos");
    }

    @Test
    void handlesAccessDeniedAsForbidden() {
        // Compruebo que una acción sin permiso responda HTTP 403.
        var response = handler.handleAccessDeniedException(
                new AccessDeniedException("No tienes permiso"), request);

        assertErrorResponse(response.getBody(), HttpStatus.FORBIDDEN, "No tienes permiso");
    }

    @Test
    void handlesRateLimitAsTooManyRequests() {
        // Compruebo la respuesta que se entrega al superar el límite de intentos.
        var response = handler.handleRateLimitExceededException(
                new RateLimitExceededException("Espera antes de volver a intentarlo"), request);

        assertErrorResponse(response.getBody(), HttpStatus.TOO_MANY_REQUESTS,
                "Espera antes de volver a intentarlo");
    }

    @Test
    void handlesUnexpectedErrorWithoutExposingInternalDetails() {
        // Compruebo que un error inesperado no muestre detalles internos al cliente.
        var response = handler.handleGlobalException(
                new IllegalStateException("detalle privado"), request);

        assertErrorResponse(response.getBody(), HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocurrió un error inesperado. Por favor, intenta de nuevo más tarde.");
        assertFalse(response.getBody().getError().contains("detalle privado"));
    }

    private void validationTarget(String value) {
        // Este método solo sirve para crear el contexto de parámetro de validación.
    }

    private void assertErrorResponse(
            ErrorResponseDto body, HttpStatus status, String expectedMessage) {
        assertNotNull(body);
        assertEquals(status.value(), body.getStatus());
        assertEquals(expectedMessage, body.getError());
        assertEquals("/api/prueba", body.getPath());
        assertNotNull(body.getTimestamp());
    }
}
