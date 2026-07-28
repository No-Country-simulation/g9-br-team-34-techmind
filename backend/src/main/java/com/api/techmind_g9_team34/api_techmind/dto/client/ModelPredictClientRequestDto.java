package com.api.techmind_g9_team34.api_techmind.dto.client;

/**
 * TM-010 — Payload interno enviado al servicio de inferencia Python.
 *
 * <p>Contrato interno, no público (TM-006 §3).
 *
 * <p><b>Por qué existe si es idéntico a {@code ContenidoRequestDTO}:</b>
 * hoy coinciden por casualidad, no por diseño. Son dos contratos con dueños
 * distintos: uno lo consume el frontend, el otro lo consume el servicio de
 * Ciencia de Datos. Si Ciencia de Datos mañana necesita un campo {@code idioma}
 * o {@code max_keywords}, este DTO cambia y la API pública no se entera.
 *
 * <p>Sin anotaciones de Bean Validation: la entrada ya fue validada en el
 * borde de la API. Revalidar acá duplicaría reglas en dos lugares.
 */
public record ModelPredictClientRequestDto(
        String titulo,
        String texto
) {
}
