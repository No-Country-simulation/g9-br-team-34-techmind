package com.api.techmind_g9_team34.api_techmind.client.mock;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Profile("mock")
public class MockModeloInferenciaClient implements ModeloInferenciaClient {

    @Override
    public ModelPredictClientResponseDto predecir(ModelPredictClientRequestDto request) {
        return new ModelPredictClientResponseDto(
                "Backend",
                0.89,
                List.of("Java", "Spring Boot", "API REST")
        );
    }
}