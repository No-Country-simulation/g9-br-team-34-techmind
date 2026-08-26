package com.api.techmind_g9_team34.api_techmind.client.mock;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Respuesta simulada del servicio de inferencia, para levantar el backend sin
 * depender del servicio de Python (TM-019).
 *
 * <p><b>Sobre las minúsculas:</b> el modelo real entrega la categoría y las
 * palabras clave en minúscula, porque el conjunto de entrenamiento está
 * normalizado así. Cuando este mock devolvía {@code "Backend"} con mayúscula,
 * una base que mezclaba contenidos creados con mock y con el servicio real
 * terminaba con dos categorías para lo mismo, y el mapa las mostraba como ramas
 * separadas. Confirmado con Ciencia de Datos y alineado acá.
 */
@Component
@Profile("mock")
public class MockModeloInferenciaClient implements ModeloInferenciaClient {

    @Override
    public ModelPredictClientResponseDto predecir(ModelPredictClientRequestDto request) {
        return new ModelPredictClientResponseDto(
                "backend",
                0.89,
                List.of("java", "spring boot", "api rest")
        );
    }
}
