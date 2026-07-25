package com.logitree.api.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * TM-007 — Entrada del endpoint POST /api/v1/contenidos.
 *
 * <p>Contrato público de la API. Las validaciones se declaran aquí mediante
 * Bean Validation (Requerimientos §6.10) y se disparan con {@code @Valid}
 * en el parámetro del controlador. No agregar validaciones manuales en el
 * controlador: si una regla no se puede expresar de forma declarativa,
 * va en la capa de servicio (§6.10).
 *
 * <p>Se usa {@code record} y no una clase con setters porque un DTO de entrada
 * debe ser inmutable una vez deserializado: elimina la posibilidad de que una
 * capa intermedia mute la solicitud original.
 */
public record ContenidoRequestDTO(

        @NotBlank(message = "El campo 'titulo' es obligatorio y no puede estar vacío.")
        @Size(max = 200, message = "El campo 'titulo' no puede superar los 200 caracteres.")
        String titulo,

        @NotBlank(message = "El campo 'texto' es obligatorio y no puede estar vacío.")
        @Size(
                min = 20,
                max = 10_000,
                message = "El campo 'texto' debe tener entre 20 y 10000 caracteres."
        )
        String texto

) {
    /**
     * Normaliza los espacios sobrantes antes de que el valor quede fijado.
     *
     * <p>Se ejecuta al construir el record, incluido cuando lo construye Jackson
     * al deserializar. Un título con espacios al inicio o al final es
     * el mismo título, y esto evita que se guarden variantes con diferencias
     * invisibles que después rompan comparaciones o búsquedas.
     *
     * <p>El {@code null} se deja pasar tal cual: quien reporta ese error es
     * {@code @NotBlank}, no el constructor. Lanzar aquí produciría un 500 en
     * lugar del 400 que corresponde.
     */
    public ContenidoRequestDTO {
        if (titulo != null) {
            titulo = titulo.strip();
        }
        if (texto != null) {
            texto = texto.strip();
        }
    }
}
