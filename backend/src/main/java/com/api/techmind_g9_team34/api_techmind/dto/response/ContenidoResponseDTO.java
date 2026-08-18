package com.api.techmind_g9_team34.api_techmind.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * TM-008 — Salida del endpoint POST /api/v1/contenidos.
 *
 * <p>Contrato público de la API.
 *
 * <p><b>Sobre los campos opcionales:</b> {@code id} y {@code fechaProcesamiento}
 * solo tienen valor si está implementada la persistencia (RF-11). Se declaran
 * desde ahora y se omiten del JSON cuando son nulos, en lugar de agregarlos
 * después y romper el contrato con el frontend a mitad de proyecto.
 *
 * <p><b>Sobre {@code informacion_adicional}:</b> el nombre en JSON usa snake_case
 * por decisión explícita del contrato (TM-006 §1), para coincidir con el ejemplo
 * literal del brief del hackathon. El resto de campos usa camelCase.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ContenidoResponseDTO(

        /** Identificador del resultado persistido. Nulo si no hay persistencia. */
        UUID id,

        /** Título original enviado por el cliente, devuelto para trazabilidad. */
        String titulo,

        /** Categoría temática predicha por el modelo (una de las 7 de TM-006 §0). */
        String categoria,

        /**
         * Confianza de la clasificación, en [0.0, 1.0].
         * Se declara {@code Double} y no {@code double} porque un valor ausente
         * con primitivo se serializaría como {@code 0.0}, que es un dato falso.
         */
        Double probabilidad,

        /** Palabras clave extraídas del texto. Nunca nulo: en el peor caso, lista vacía. */
        @JsonProperty("informacion_adicional")
        List<String> informacionAdicional,

        /** Momento del procesamiento en UTC. Nulo si no hay persistencia. */
        Instant fechaProcesamiento

) {
    /**
     * Blinda la lista frente a modificaciones externas y frente a {@code null}.
     *
     * <p>{@link List#copyOf} devuelve una lista inmutable, así que el record es
     * inmutable de verdad y no solo en apariencia.
     */
    public ContenidoResponseDTO {
        informacionAdicional = (informacionAdicional == null)
                ? List.of()
                : List.copyOf(informacionAdicional);
    }
}
