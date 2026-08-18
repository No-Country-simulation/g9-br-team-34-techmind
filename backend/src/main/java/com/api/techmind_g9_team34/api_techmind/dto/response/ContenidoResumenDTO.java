package com.api.techmind_g9_team34.api_techmind.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * TM-037 — Vista ligera de un contenido procesado para listados.
 *
 * <p>A diferencia de {@link ContenidoResponseDTO}, no expone {@code probabilidad}
 * ni {@code informacionAdicional} (más económico al listar muchos elementos).
 */
public record ContenidoResumenDTO(
        UUID id,
        String titulo,
        String categoria,
        Instant fechaProcesamiento
) {
}