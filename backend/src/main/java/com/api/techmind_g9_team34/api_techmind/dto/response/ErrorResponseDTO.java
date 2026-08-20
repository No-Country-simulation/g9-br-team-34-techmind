package com.api.techmind_g9_team34.api_techmind.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.List;

/**
 * Formato estándar de error de la API (TM-009, ampliado en S5-01).
 *
 * <p>Toda respuesta de error sale con esta forma, sin excepción, y
 * {@code GlobalExceptionHandler} es el único lugar que la construye.
 *
 * <p><b>S5-01 — Por qué hay tres textos y no uno.</b> Antes, {@code message}
 * cargaba con dos trabajos incompatibles: explicarle el fallo a una persona y
 * describirlo para quien depura. Cumplir con uno estropeaba el otro, y a la
 * interfaz llegaban frases que nadie sabía qué hacer con ellas. Ahora cada
 * texto tiene un destinatario:
 *
 * <ul>
 *   <li>{@code mensajeUsuario} — qué pasó, en lenguaje llano y sin nombres de
 *       excepciones ni rutas internas. Es lo que la interfaz muestra.</li>
 *   <li>{@code sugerencia} — qué puede hacer quien lo lee. Nulo cuando no hay
 *       nada accionable: inventar un consejo vacío es peor que no darlo.</li>
 *   <li>{@code message} — descripción técnica, para registros y depuración.</li>
 * </ul>
 *
 * <p><b>Sobre no exponer el detalle técnico:</b> los mensajes de excepción
 * pueden filtrar rutas de archivos, nombres de tablas o direcciones de
 * servicios internos. Por eso el handler del 500 emite un texto fijo en
 * {@code message} en lugar del {@code getMessage()} de la excepción, y la traza
 * completa queda sólo en el registro del servidor.
 *
 * <p><b>Sobre el uso de {@link HttpStatus} en las fábricas:</b> reciben el
 * status y derivan de él tanto el número como la frase, lo que hace imposible
 * responder {@code status: 400} con {@code error: "Not Found"}.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponseDTO(

        Instant timestamp,
        int status,
        String error,

        /** Descripción técnica del fallo. Para registros y depuración, no para mostrar. */
        String message,

        /** S5-01 — Qué pasó, redactado para quien usa la aplicación. */
        String mensajeUsuario,

        /** S5-01 — Qué puede hacer. Nulo si no hay ninguna acción útil que ofrecer. */
        String sugerencia,

        String path,

        /**
         * Detalle por campo. Sólo se completa en errores de validación (400);
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
     * Error sin detalle por campo, con texto para la persona y sugerencia.
     *
     * @param status         código HTTP a devolver
     * @param message        descripción técnica, para el registro
     * @param mensajeUsuario qué pasó, en lenguaje llano
     * @param sugerencia     qué puede hacer; nulo si no hay nada que sugerir
     * @param path           ruta de la solicitud que falló
     */
    public static ErrorResponseDTO of(HttpStatus status,
                                      String message,
                                      String mensajeUsuario,
                                      String sugerencia,
                                      String path) {
        return new ErrorResponseDTO(
                Instant.now(),
                status.value(),
                status.getReasonPhrase(),
                message,
                mensajeUsuario,
                sugerencia,
                path,
                null
        );
    }

    /**
     * Variante sin sugerencia, para fallos donde no hay acción que ofrecer.
     */
    public static ErrorResponseDTO of(HttpStatus status,
                                      String message,
                                      String mensajeUsuario,
                                      String path) {
        return of(status, message, mensajeUsuario, null, path);
    }

    /**
     * Error de validación con el detalle de cada campo inválido (400).
     *
     * @param message        descripción técnica
     * @param mensajeUsuario qué pasó, en lenguaje llano
     * @param sugerencia     qué puede hacer
     * @param path           ruta de la solicitud que falló
     * @param errores        campos que no pasaron la validación
     */
    public static ErrorResponseDTO deValidacion(String message,
                                                String mensajeUsuario,
                                                String sugerencia,
                                                String path,
                                                List<CampoInvalido> errores) {
        return new ErrorResponseDTO(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                message,
                mensajeUsuario,
                sugerencia,
                path,
                errores
        );
    }
}
