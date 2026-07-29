package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;

public interface ContenidoService {
    ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request);
}
