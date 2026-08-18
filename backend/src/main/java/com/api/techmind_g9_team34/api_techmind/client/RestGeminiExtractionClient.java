package com.api.techmind_g9_team34.api_techmind.client;

import com.api.techmind_g9_team34.api_techmind.config.GeminiConfig;
import com.api.techmind_g9_team34.api_techmind.dto.client.GeminiGenerateContentRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.GeminiGenerateContentResponseDto;
import com.api.techmind_g9_team34.api_techmind.exception.ExtraccionException;
import com.api.techmind_g9_team34.api_techmind.util.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Base64;

/**
 * Implementación real de {@link GeminiExtractionClient}, usando el
 * RestClient síncrono (ver WebClientConfig), en el mismo estilo que
 * {@code RestModeloInferenciaClient}.
 */
@Component
@Profile("!mock")
public class RestGeminiExtractionClient implements GeminiExtractionClient {

    private static final Logger logger = LoggerFactory.getLogger(RestGeminiExtractionClient.class);

    private static final int MAX_ATTEMPTS = 2;

    private final RestClient restClient;
    private final GeminiConfig config;

    public RestGeminiExtractionClient(RestClient geminiRestClient, GeminiConfig config) {
        this.restClient = geminiRestClient;
        this.config = config;
    }

    @Override
    public String extraerDeArchivo(byte[] contenido, String mimeType) {
        String base64Data = Base64.getEncoder().encodeToString(contenido);
        GeminiGenerateContentRequestDto request = GeminiGenerateContentRequestDto.deArchivo(
                Constants.PROMPT_EXTRACCION_GEMINI, mimeType, base64Data
        );
        return llamarConReintentos(request);
    }

    @Override
    public String limpiar(String tituloCrudo, String textoCrudo) {
        String contenido = "TÍTULO CRUDO:\n" + tituloCrudo + "\n\nTEXTO CRUDO:\n" + textoCrudo;
        GeminiGenerateContentRequestDto request = GeminiGenerateContentRequestDto.deTexto(
                Constants.PROMPT_LIMPIEZA_GEMINI, contenido
        );
        return llamarConReintentos(request);
    }

    private String llamarConReintentos(GeminiGenerateContentRequestDto request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return ejecutarLlamada(request);

            } catch (ExtraccionException e) {
                // Ya viene envuelto desde ejecutarLlamada (ej. error HTTP de Gemini);
                // no tiene sentido reintentar un 4xx/5xx explícito de la API.
                throw e;

            } catch (RestClientException e) {
                // Cubre tanto fallas de conexión como timeouts al leer la
                // respuesta (este último no es ResourceAccessException,
                // por eso se captura la clase base más general).
                logger.warn(
                        "Intento {} de {} falló al comunicarse con Gemini: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());

                if (attempt == MAX_ATTEMPTS) {
                    throw new ExtraccionException(
                            "El servicio de extracción (Gemini) no está disponible en este momento.", e);
                }
            }
        }

        throw new IllegalStateException("No debería alcanzarse");
    }

    private String ejecutarLlamada(GeminiGenerateContentRequestDto request) {
        String uri = "/v1beta/models/%s:generateContent?key=%s"
                .formatted(config.getModel(), config.getApiKey());

        try {
            GeminiGenerateContentResponseDto respuesta = restClient.post()
                    .uri(config.getBaseUrl() + uri)
                    .body(request)
                    .retrieve()
                    .body(GeminiGenerateContentResponseDto.class);

            if (respuesta == null) {
                throw new ExtraccionException("Gemini devolvió una respuesta vacía.");
            }

            return respuesta.textoPrincipal();

        } catch (HttpStatusCodeException e) {
            logger.warn("Gemini respondió con un error HTTP: {}. Cuerpo: {}",
                    e.getStatusCode(), e.getResponseBodyAsString());
            throw new ExtraccionException(
                    "El servicio de extracción (Gemini) no está disponible en este momento.", e);
        }
    }
}