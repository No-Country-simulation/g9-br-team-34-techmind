package com.api.techmind_g9_team34.api_techmind.mapper;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

@Component
public class ContenidoMapper {

    public ModelPredictClientRequestDto toClientRequest(ContenidoRequestDTO request) {
        return new ModelPredictClientRequestDto(request.titulo(), request.texto());
    }

    /**
     * Convierte la entrada + salida del modelo de inferencia en una entidad lista
     * para persistir.
     *
     * <p>No se setean {@code id} ni {@code fechaProcesamiento}: los asigna
     * Hibernate al insertar (TM-035).
     *
     * @param request        solicitud del cliente
     * @param clientResponse respuesta del servicio de inferencia
     * @return entidad sin persistir
     */
    public ContenidoAnalizado toEntity(ContenidoRequestDTO request, ModelPredictClientResponseDto clientResponse) {
        return ContenidoAnalizado.builder()
                .titulo(request.titulo())
                .texto(request.texto())
                .categoria(clientResponse.categoria())
                .probabilidad(clientResponse.probabilidad())
                .palabrasClave(new ArrayList<>(clientResponse.informacionAdicional()))
                .build();
    }

    /**
     * Convierte una entidad persistida en el DTO de respuesta público.
     *
     * @param entity entidad ya persistida (con id y fechaProcesamiento no nulos)
     * @return DTO de respuesta
     */
    public ContenidoResponseDTO toResponseDTO(ContenidoAnalizado entity) {
        return new ContenidoResponseDTO(
                entity.getId(),
                entity.getTitulo(),
                entity.getCategoria(),
                entity.getProbabilidad(),
                entity.getPalabrasClave(),
                entity.getFechaProcesamiento()
        );
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
