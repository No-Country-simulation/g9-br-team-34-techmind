package com.api.techmind_g9_team34.api_techmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para la API de Gemini (Google), usada en el pipeline de
 * extracción de archivos: como fallback cuando los parsers determinísticos
 * (PDFBox/POI/jsoup) fallan, y como paso de limpieza obligatorio sobre el
 * texto que sí lograron extraer.
 *
 * Carga automáticamente las propiedades definidas con el prefijo
 * "techmind.gemini" utilizando @ConfigurationProperties. El bean de
 * RestClient correspondiente vive en WebClientConfig, junto al resto
 * de clientes HTTP del proyecto.
 */
@Configuration
@ConfigurationProperties(prefix = "techmind.gemini")
@Getter
@Setter
public class GeminiConfig {

    /** API key de Gemini (Google AI Studio). */
    private String apiKey;

    /** Modelo a usar, ej. "gemini-2.5-flash". */
    private String model = "gemini-2.5-flash";

    /** URL base de la API de Gemini. */
    private String baseUrl = "https://generativelanguage.googleapis.com";

    /** Timeout en milisegundos para las llamadas al fallback/limpieza. */
    private int timeoutMs = 60_000;
}
