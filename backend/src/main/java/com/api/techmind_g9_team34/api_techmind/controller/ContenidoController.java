package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/contenidos")
public class ContenidoController {

    private final ContenidoService contenidoService;

    public ContenidoController(ContenidoService contenidoService) {
        this.contenidoService = contenidoService;
    }

    @PostMapping
    public ResponseEntity<ContenidoResponseDTO> procesarContenido(
            @Valid @RequestBody ContenidoRequestDTO request) {
        ContenidoResponseDTO response = contenidoService.procesarContenido(request);
        return ResponseEntity.ok(response);
    }
}
