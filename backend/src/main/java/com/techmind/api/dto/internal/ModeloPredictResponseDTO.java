package com.techmind.api.dto.internal;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * TM-010 — Respuesta interna recibida del servicio de inferencia Python.
 *
 * <p>Contrato interno (docs/contrato-backend-ds.md §4). El servicio Python
 * responde en snake_case por convención de Python (PEP 8), de modo que
 * {@code informacion_adicional} se mapea explícitamente al campo camelCase
 * de Java.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} permite que Ciencia
 * de Datos agregue campos nuevos a su respuesta sin romper el backend. Sin
 * esto, el día que agreguen un campo de diagnóstico, todas las solicitudes
 * empiezan a fallar con 503 y se pierde media tarde buscando la causa.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ModeloPredictResponseDTO(

        String categoria,

        /** Nulo si el enfoque elegido por Ciencia de Datos no produce probabilidad (D-01). */
        Double probabilidad,

        @JsonProperty("informacion_adicional")
        List<String> informacionAdicional

) {
    /**
     * Normaliza la lista ausente a lista vacía.
     *
     * <p>Se hace en el borde de entrada, apenas Jackson deserializa, y no más
     * adelante en el servicio: así ninguna capa posterior necesita comprobar
     * si es nula.
     */
    public ModeloPredictResponseDTO {
        informacionAdicional = (informacionAdicional == null)
                ? List.of()
                : List.copyOf(informacionAdicional);
    }
}
