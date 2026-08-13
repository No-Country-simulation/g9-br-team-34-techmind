package com.api.techmind_g9_team34.api_techmind.dto.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Respuesta cruda del endpoint {@code generateContent} de Gemini.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} porque solo nos
 * interesa el texto generado; Gemini incluye metadatos adicionales
 * (safety ratings, uso de tokens, etc.) que no necesitamos mapear.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GeminiGenerateContentResponseDto(
        List<Candidate> candidates
) {

    /**
     * Extrae el texto de la primera respuesta candidata.
     * Devuelve cadena vacía si Gemini no devolvió contenido (ej. bloqueado
     * por políticas de seguridad), para que el llamador decida cómo
     * manejarlo sin lanzar NullPointerException.
     */
    public String textoPrincipal() {
        if (candidates == null || candidates.isEmpty()) {
            return "";
        }
        Content content = candidates.get(0).content();
        if (content == null || content.parts() == null || content.parts().isEmpty()) {
            return "";
        }
        return content.parts().get(0).text();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Candidate(Content content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Content(List<Part> parts) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Part(String text) {
    }
}
