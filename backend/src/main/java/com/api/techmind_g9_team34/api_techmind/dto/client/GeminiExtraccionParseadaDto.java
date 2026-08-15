package com.api.techmind_g9_team34.api_techmind.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Representa el JSON {@code {"titulo": ..., "texto": ...}} que le pedimos
 * a Gemini que devuelva dentro de su respuesta de texto (ver prompts en
 * {@code Constants}).
 *
 * <p>Es un DTO intermedio: existe porque la respuesta de Gemini es texto
 * libre, no un objeto tipado por la API. Una vez parseado aquí, se mapea
 * a {@code ContenidoRequestDTO} para seguir el flujo normal del backend.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiExtraccionParseadaDto(
        String titulo,
        String texto
) {
}
