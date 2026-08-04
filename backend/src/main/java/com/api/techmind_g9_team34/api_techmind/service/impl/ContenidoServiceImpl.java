package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContenidoServiceImpl implements ContenidoService {

    private final ModeloInferenciaClient modeloClient;
    private final ContenidoMapper mapper;
    private final ContenidoAnalizadoRepository contenidoRepository;

    public ContenidoServiceImpl(
            ModeloInferenciaClient modeloClient,
            ContenidoMapper mapper,
            ContenidoAnalizadoRepository contenidoRepository) {
        this.modeloClient = modeloClient;
        this.mapper = mapper;
        this.contenidoRepository = contenidoRepository;
    }

    @Override
    @Transactional
    public ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request) {
        ModelPredictClientRequestDto clientRequest = mapper.toClientRequest(request);
        ModelPredictClientResponseDto clientResponse;
        try {
            clientResponse = modeloClient.predecir(clientRequest);
        } catch (Exception e) {
            throw new ModeloServiceException(
                    "El servicio de análisis no está disponible en este momento.", e);
        }
        ContenidoAnalizado entity = mapper.toEntity(request, clientResponse);
        ContenidoAnalizado persistido = contenidoRepository.save(entity);
        return mapper.toResponseDTO(persistido);
    }

    @Override
    @Transactional(readOnly = true)
    public ContenidoResponseDTO obtenerContenido(UUID id) {
        ContenidoAnalizado entity = contenidoRepository.findById(id)
                .orElseThrow(() -> new ContenidoNoEncontradoException(
                        "No existe un contenido procesado con el id indicado."));
        return mapper.toResponseDTO(entity);
    }
}