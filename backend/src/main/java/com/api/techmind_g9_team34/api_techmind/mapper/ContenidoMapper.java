package com.api.techmind_g9_team34.api_techmind.mapper;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import org.springframework.stereotype.Component;

@Component
public class ContenidoMapper {

    public ModelPredictClientRequestDto toClientRequest(ContenidoRequestDTO request) {
        return new ModelPredictClientRequestDto(request.titulo(), request.texto());
    }

    public ContenidoResponseDTO toResponse(ContenidoRequestDTO request, ModelPredictClientResponseDto clientResponse) {
        return new ContenidoResponseDTO(
                null,
                request.titulo(),
                clientResponse.categoria(),
                clientResponse.probabilidad(),
                clientResponse.informacionAdicional(),
                null
        );
    }
}
