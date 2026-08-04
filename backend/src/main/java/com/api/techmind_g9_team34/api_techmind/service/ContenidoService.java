package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;

import java.util.UUID;

public interface ContenidoService {
    ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request);

    /**
     * Obtiene un contenido previamente procesado por su id.
     *
     * @param id identificador del contenido
     * @return el contenido procesado
     * @throws com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException
     *         si no existe un contenido con ese id
     */
    ContenidoResponseDTO obtenerContenido(UUID id);
}
