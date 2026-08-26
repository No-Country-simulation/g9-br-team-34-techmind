package com.api.techmind_g9_team34.api_techmind.mapper;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
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
     * @param resumen        resumen generado por Gemini a partir del texto
     * @return entidad sin persistir
     */
    public ContenidoAnalizado toEntity(ContenidoRequestDTO request, ModelPredictClientResponseDto clientResponse, String resumen) {
        return ContenidoAnalizado.builder()
                .titulo(request.titulo())
                .texto(request.texto())
                .resumen(resumen)
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
                entity.getTexto(),
                entity.getResumen(),
                entity.getCategoria(),
                entity.getProbabilidad(),
                entity.getPalabrasClave(),
                entity.getFechaProcesamiento()
        );
    }

    // TODO: revisar - toResponse() no persiste, así que no hay resumen generado
    // todavía en este punto. Queda en null hasta que se defina cuándo se llama
    // a Gemini en este flujo (antes o después de este método).
    public ContenidoResponseDTO toResponse(ContenidoRequestDTO request, ModelPredictClientResponseDto clientResponse) {
        return new ContenidoResponseDTO(
                null,
                request.titulo(),
                request.texto(),
                null,
                clientResponse.categoria(),
                clientResponse.probabilidad(),
                clientResponse.informacionAdicional(),
                null
        );
    }

    /**
     * Convierte una entidad en su visión ligera para listados (TM-037).
     *
     * <p>Omite {@code probabilidad} e {@code informacionAdicional} a propósito:
     * el listado no necesita esos campos y así se evita cargar la colección
     * perezosa {@code palabrasClave} (que dispararía LazyInitializationException
     * fuera de transacción).
     *
     * @param entity entidad persistida
     * @return resumen del contenido
     */
    public ContenidoResumenDTO toResumenDTO(ContenidoAnalizado entity) {
        return new ContenidoResumenDTO(
                entity.getId(),
                entity.getTitulo(),
                entity.getCategoria(),
                entity.getFechaProcesamiento()
        );
    }
}