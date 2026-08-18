package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.response.CategoriaDTO;
import com.api.techmind_g9_team34.api_techmind.service.CategoriaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * TM-038 — Endpoint de categorías.
 *
 * <p>Vive en un {@code CategoriaController} dedicado (no en
 * {@link ContenidoController}) por decisión de aislamiento (TM-070).
 */
@RestController
@RequestMapping("/api/v1/categorias")
public class CategoriaController {

    private static final Logger logger =
            LoggerFactory.getLogger(CategoriaController.class);

    private final CategoriaService categoriaService;

    public CategoriaController(CategoriaService categoriaService) {
        this.categoriaService = categoriaService;
    }

    @GetMapping
    public ResponseEntity<List<CategoriaDTO>> listarCategorias() {
        logger.info("Solicitud recibida para GET /api/v1/categorias");
        return ResponseEntity.ok(categoriaService.listarCategorias());
    }
}