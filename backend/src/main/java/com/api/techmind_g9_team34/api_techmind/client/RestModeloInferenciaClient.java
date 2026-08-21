package com.api.techmind_g9_team34.api_techmind.client;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
@Profile("!mock")
public class RestModeloInferenciaClient implements ModeloInferenciaClient {

    private static final Logger logger =
            LoggerFactory.getLogger(RestModeloInferenciaClient.class);

    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;
    private final String baseUrl;

    public RestModeloInferenciaClient(
            RestClient inferenceRestClient,
            @Value("${techmind.inference-service.base-url}") String baseUrl) {
        this.restClient = inferenceRestClient;
        this.baseUrl = baseUrl;
    }

    @Override
    public ModelPredictClientResponseDto predecir(ModelPredictClientRequestDto request) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                return ejecutarLlamada(request);

            } catch (HttpStatusCodeException e) {
                // Errores HTTP (4xx, 5xx) del servicio: no reintentar, fallar rápido
                logger.warn(
                        "El servicio de inferencia respondió con error HTTP {}: {}",
                        e.getStatusCode(), e.getMessage());
                throw new ModeloServiceException(
                        "El servicio de análisis no está disponible en este momento.", e);

            } catch (ResourceAccessException e) {
                // Errores de conexión/timeout: reintentar
                logger.warn(
                        "Intento {} de {} falló al conectar con el servicio de inferencia: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());

                if (attempt == MAX_ATTEMPTS) {
                    throw new ModeloServiceException(
                            "El servicio de análisis no está disponible en este momento.", e);
                }
            } catch (RestClientException e) {
                // Otros errores de RestClient (conversión, status desconocido, etc.): reintentar
                logger.warn(
                        "Intento {} de {} falló con error de cliente REST: {}",
                        attempt, MAX_ATTEMPTS, e.getMessage());

                if (attempt == MAX_ATTEMPTS) {
                    throw new ModeloServiceException(
                            "El servicio de análisis no está disponible en este momento.", e);
                }
            }
        }

        throw new IllegalStateException("No debería alcanzarse");
    }

    private ModelPredictClientResponseDto ejecutarLlamada(ModelPredictClientRequestDto request) {
        try {
            return restClient.post()
                    .uri(baseUrl + "/predict")
                    .body(request)
                    .retrieve()
                    .body(ModelPredictClientResponseDto.class);

        } catch (HttpStatusCodeException e) {
            logger.warn(
                    "El servicio de inferencia respondió con un error HTTP: {}",
                    e.getStatusCode());
            throw new ModeloServiceException(
                    "El servicio de análisis no está disponible en este momento.", e);
        }
    }
}