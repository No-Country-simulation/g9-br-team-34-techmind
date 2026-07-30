package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import org.springframework.stereotype.Service;

@Service
public class ContenidoServiceImpl implements ContenidoService {

    private final ModeloInferenciaClient modeloClient;
    private final ContenidoMapper mapper;

    public ContenidoServiceImpl(ModeloInferenciaClient modeloClient, ContenidoMapper mapper) {
        this.modeloClient = modeloClient;
        this.mapper = mapper;
    }

    @Override
    public ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request) {
        ModelPredictClientRequestDto clientRequest = mapper.toClientRequest(request);
        ModelPredictClientResponseDto clientResponse;
        try {
            clientResponse = modeloClient.predecir(clientRequest);
        } catch (Exception e) {
            throw new ModeloServiceException(
                    "El servicio de análisis no está disponible en este momento.", e);
        }
        return mapper.toResponse(request, clientResponse);
    }
}