package com.api.techmind_g9_team34.api_techmind.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * TM-038 — Categoría expuesta en {@code GET /api/v1/categorias}.
 *
 * <p>La {@code cantidadProcesados} se puebla recién en TM-067; hasta entonces es
 * {@code null} y se omite del JSON gracias a {@link JsonInclude}.
 */
public record CategoriaDTO(
        String categoria,
        @JsonInclude(JsonInclude.Include.NON_NULL) Long cantidadProcesados
) {
}