package com.api.techmind_g9_team34.api_techmind.dto.client;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Payload enviado al endpoint {@code generateContent} de la API de Gemini.
 *
 * <p>Contrato interno, no público. Refleja el formato exacto que espera
 * Gemini, incluyendo los nombres en snake_case ({@code inline_data},
 * {@code mime_type}) que Java no sigue por convención — de ahí el uso
 * de {@code @JsonProperty}.
 */
public record GeminiGenerateContentRequestDto(
        List<Content> contents,
        GenerationConfig generationConfig
) {

    private static final GenerationConfig CONFIG_DEFAULT = new GenerationConfig(32_768);

    public static GeminiGenerateContentRequestDto deTexto(String prompt, String texto) {
        return new GeminiGenerateContentRequestDto(
                List.of(new Content(List.of(Part.deTexto(prompt + "\n\nContenido:\n" + texto)))),
                CONFIG_DEFAULT
        );
    }

    public static GeminiGenerateContentRequestDto deArchivo(String prompt, String mimeType, String base64Data) {
        return new GeminiGenerateContentRequestDto(
                List.of(new Content(List.of(
                        Part.deTexto(prompt),
                        Part.deArchivo(mimeType, base64Data)
                ))),
                CONFIG_DEFAULT
        );
    }

    public record Content(List<Part> parts) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Part(
            String text,
            @JsonProperty("inline_data") InlineData inlineData
    ) {
        public static Part deTexto(String texto) {
            return new Part(texto, null);
        }

        public static Part deArchivo(String mimeType, String base64Data) {
            return new Part(null, new InlineData(mimeType, base64Data));
        }
    }

    public record InlineData(
            @JsonProperty("mime_type") String mimeType,
            String data
    ) {
    }

    public record GenerationConfig(
            @JsonProperty("maxOutputTokens") int maxOutputTokens
    ) {
    }
}