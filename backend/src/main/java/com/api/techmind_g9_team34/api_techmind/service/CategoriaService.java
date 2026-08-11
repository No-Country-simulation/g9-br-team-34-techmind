package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.response.CategoriaDTO;

import java.util.List;

/**
 * TM-038 — Servicio de categorías.
 */
public interface CategoriaService {

    /**
     * Lista las categorías con su cantidad de contenidos procesados.
     *
     * @return categorías ordenadas; vacía si no hay contenidos
     */
    List<CategoriaDTO> listarCategorias();
}