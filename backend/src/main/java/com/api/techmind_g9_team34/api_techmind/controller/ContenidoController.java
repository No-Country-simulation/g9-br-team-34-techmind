package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contenidos")
public class ContenidoController {

    private static final Logger logger =
            LoggerFactory.getLogger(ContenidoController.class);

    private final ContenidoService contenidoService;

    public ContenidoController(ContenidoService contenidoService) {
        this.contenidoService = contenidoService;
    }

    @PostMapping
    public ResponseEntity<ContenidoResponseDTO> procesarContenido(
            @Valid @RequestBody ContenidoRequestDTO request,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoResponseDTO response =
                contenidoService.procesarContenido(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContenidoResponseDTO> obtenerContenido(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoResponseDTO response = contenidoService.obtenerContenido(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PaginaDTO<ContenidoResumenDTO>> listarContenidos(
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "palabraClave", required = false) String palabraClave,
            Pageable pageable,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        PaginaDTO<ContenidoResumenDTO> response =
                contenidoService.listarContenidos(categoria, palabraClave, pageable);

        return ResponseEntity.ok(response);
    }
}