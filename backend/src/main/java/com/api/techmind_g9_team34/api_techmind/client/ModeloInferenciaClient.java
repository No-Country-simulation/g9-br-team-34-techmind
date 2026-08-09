package com.api.techmind_g9_team34.api_techmind.client;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;

public interface ModeloInferenciaClient {
    ModelPredictClientResponseDto predecir(ModelPredictClientRequestDto request);
}
