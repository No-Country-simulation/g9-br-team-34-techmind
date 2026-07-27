package com.api.techmind_g9_team34.api_techmind.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TM-010 — Respuesta interna recibida del servicio de inferencia Python.
 *
 * <p>Contrato interno (TM-006 §4). El servicio Python responde en snake_case
 * por convención de Python (PEP 8), de modo que {@code informacion_adicional}
 * se mapea explícitamente al campo camelCase de Java.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} permite que Ciencia
 * de Datos agregue campos nuevos a su respuesta sin romper el backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModelPredictClientResponseDto(

        String categoria,

        /**
         * Confianza de la clasificación, en [0.0, 1.0].
         *
         * <p>El modelo es de clasificación (TM-006 §0, D-01), por lo que en
         * condiciones normales siempre llega un valor. Se mantiene {@code Double}
         * (envoltorio, no primitivo) por defensa: puede ser nulo ante una
         * respuesta inesperada o malformada del servicio de inferencia, y en ese
         * caso conviene un {@code null} explícito antes que un {@code 0.0} falso.
         */
        Double probabilidad,

        @JsonProperty("informacion_adicional")
        List<String> informacionAdicional

) {
    /**
     * Normaliza la lista ausente a lista vacía.
     *
     * <p>Se hace en el borde de entrada, apenas Jackson deserializa, para que
     * ninguna capa posterior necesite comprobar si es nula.
     */
    public ModelPredictClientResponseDto {
        informacionAdicional = (informacionAdicional == null)
                ? List.of()
                : List.copyOf(informacionAdicional);
    }
}
