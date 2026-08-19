package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.response.MetricasDTO;
import com.api.techmind_g9_team34.api_techmind.service.MetricaService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * S5-14 — Tablero de métricas del repositorio.
 *
 * <p>Se separa de {@code ContenidoController} porque no opera sobre el recurso
 * contenido: agrega sobre todos ellos. Es el mismo criterio con el que TM-070
 * sacó las categorías a su propio controlador.
 */
@RestController
@RequestMapping("/api/v1/metricas")
public class MetricaController {

    private static final Logger logger = LoggerFactory.getLogger(MetricaController.class);

    private final MetricaService metricaService;

    public MetricaController(MetricaService metricaService) {
        this.metricaService = metricaService;
    }

    /**
     * Devuelve el tablero completo.
     *
     * <p>Siempre responde 200: con la base vacía devuelve el tablero en ceros,
     * porque un repositorio sin contenidos es un estado válido y no un 404.
     */
    @GetMapping
    public ResponseEntity<MetricasDTO> obtenerMetricas(HttpServletRequest httpRequest) {
        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());
        return ResponseEntity.ok(metricaService.obtenerMetricas());
    }
}
