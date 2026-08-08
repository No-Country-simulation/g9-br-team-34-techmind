package com.api.techmind_g9_team34.api_techmind.exception;

import com.api.techmind_g9_team34.api_techmind.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler =
            new GlobalExceptionHandler();

    private final HttpServletRequest request =
            mock(HttpServletRequest.class);

    @Test
    void debeTraducirValidacionExceptionA400() {
        when(request.getRequestURI())
                .thenReturn("/api/v1/contenidos");

        ValidacionException exception =
                new ValidacionException("Datos inválidos");

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleValidacion(exception, request);

        assertEquals(
                HttpStatus.BAD_REQUEST,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        ErrorResponseDTO body = response.getBody();

        assertEquals(400, body.status());
        assertEquals("Bad Request", body.error());
        assertEquals("Datos inválidos", body.message());
        assertEquals("/api/v1/contenidos", body.path());
        assertNotNull(body.timestamp());
    }

    @Test
    void debeTraducirContenidoNoEncontradoA404() {
        when(request.getRequestURI())
                .thenReturn("/api/v1/contenidos/123");

        ContenidoNoEncontradoException exception =
                new ContenidoNoEncontradoException(
                        "Contenido no encontrado"
                );

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleNoEncontrado(exception, request);

        assertEquals(
                HttpStatus.NOT_FOUND,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        ErrorResponseDTO body = response.getBody();

        assertEquals(404, body.status());
        assertEquals("Not Found", body.error());
        assertEquals(
                "Contenido no encontrado",
                body.message()
        );
        assertEquals(
                "/api/v1/contenidos/123",
                body.path()
        );
        assertNotNull(body.timestamp());
        assertNull(body.errores());
    }

    @Test
    void debeTraducirModeloServiceExceptionA503() {
        when(request.getRequestURI())
                .thenReturn("/api/v1/contenidos");

        ModeloServiceException exception =
                new ModeloServiceException(
                        "Servicio de inferencia no disponible"
                );

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleModeloService(exception, request);

        assertEquals(
                HttpStatus.SERVICE_UNAVAILABLE,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        ErrorResponseDTO body = response.getBody();

        assertEquals(503, body.status());
        assertEquals(
                "Service Unavailable",
                body.error()
        );
        assertEquals(
                "Servicio de inferencia no disponible",
                body.message()
        );
        assertEquals(
                "/api/v1/contenidos",
                body.path()
        );
        assertNotNull(body.timestamp());
        assertNull(body.errores());
    }

    @Test
    void debeTraducirProcesamientoExceptionA422() {
        when(request.getRequestURI())
                .thenReturn("/api/v1/contenidos");

        ProcesamientoException exception =
                new ProcesamientoException(
                        "No fue posible procesar el contenido"
                );

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleProcesamiento(exception, request);

        assertEquals(
                HttpStatus.UNPROCESSABLE_ENTITY,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        ErrorResponseDTO body = response.getBody();

        assertEquals(422, body.status());
        assertEquals(
                "Unprocessable Entity",
                body.error()
        );
        assertEquals(
                "No fue posible procesar el contenido",
                body.message()
        );
        assertEquals(
                "/api/v1/contenidos",
                body.path()
        );
        assertNotNull(body.timestamp());
        assertNull(body.errores());
    }

    @Test
    void debeTraducirExcepcionGenericaA500() {
        when(request.getRequestURI())
                .thenReturn("/api/v1/contenidos");

        Exception exception =
                new Exception("Error interno");

        ResponseEntity<ErrorResponseDTO> response =
                handler.handleGeneral(exception, request);

        assertEquals(
                HttpStatus.INTERNAL_SERVER_ERROR,
                response.getStatusCode()
        );

        assertNotNull(response.getBody());

        ErrorResponseDTO body = response.getBody();

        assertEquals(500, body.status());
        assertEquals(
                "Internal Server Error",
                body.error()
        );
        assertEquals(
                "Ocurrió un error inesperado.",
                body.message()
        );
        assertEquals(
                "/api/v1/contenidos",
                body.path()
        );
        assertNotNull(body.timestamp());
        assertNull(body.errores());
    }
}