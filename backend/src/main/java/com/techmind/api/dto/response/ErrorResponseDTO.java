package com.techmind.api.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * TM-009 — Formato estándar de error para toda la API.
 *
 * <p>Requerimientos §6.11 y §6.12. Toda respuesta de error de la API sale con
 * esta forma, sin excepción. El {@code GlobalExceptionHandler} (TM-020) es el
 * único lugar que construye instancias de este DTO.
 *
 * <p><b>Sobre el uso de {@link HttpStatus}:</b> las fábricas estáticas reciben
 * un {@code HttpStatus} en lugar de un {@code int} y un {@code String} sueltos.
 * Eso hace imposible el error clásico de responder {@code status: 400} con
 * {@code error: "Not Found"}, porque ambos campos se derivan del mismo valor.
 * Acopla el DTO a spring-web, que de todos modos es una dependencia inevitable
 * de este proyecto.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(

        Instant timestamp,
        int status,
        String error,
        String message,
        String path,

        /**
         * Detalle por campo. Solo se completa en errores de validación (400);
         * en el resto queda nulo y Jackson lo omite del JSON.
         */
        List<CampoInvalido> errores

) {

    /** Par campo/mensaje para los errores de Bean Validation. */
    public record CampoInvalido(String campo, String mensaje) {
    }

    public ErrorResponseDTO {
        if (errores != null) {
            errores = List.copyOf(errores);
        }
    }

    /**
     * Error sin detalle por campo. Es el caso de 404, 422, 500 y 503.
     *
     * @param status código HTTP a devolver
     * @param message mensaje orientado al consumidor de la API, sin datos internos
     * @param path ruta de la solicitud que falló
     */
    public static ErrorResponseDTO of(HttpStatus status, String message, String path) {
        return new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                path,
                null
        );
    }

    /**
     * Error de validación con el detalle de cada campo inválido (400).
     *
     * @param message mensaje general del error
     * @param path ruta de la solicitud que falló
     * @param errores lista de campos que no pasaron la validación
     */
    public static ErrorResponseDTO deValidacion(
            String message,
            String path,
            List<CampoInvalido> errores
    ) {
        return new ErrorResponseDTO(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                path,
                errores
        );
    }
}
