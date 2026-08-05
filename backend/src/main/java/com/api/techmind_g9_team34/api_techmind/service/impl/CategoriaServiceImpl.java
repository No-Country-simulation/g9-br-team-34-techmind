package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.dto.response.CategoriaDTO;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.repository.projection.ConteoCategoria;
import com.api.techmind_g9_team34.api_techmind.service.CategoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TM-038 — Implementación de {@link CategoriaService}.
 *
 * <p>Creado en TM-038 con {@code cantidadProcesados} en {@code null}; desde
 * TM-067 se puebla con el conteo real agrupado por categoría.
 */
@Service
public class CategoriaServiceImpl implements CategoriaService {

    private final ContenidoAnalizadoRepository contenidoRepository;

    public CategoriaServiceImpl(ContenidoAnalizadoRepository contenidoRepository) {
        this.contenidoRepository = contenidoRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoriaDTO> listarCategorias() {
        return contenidoRepository.contarPorCategoria().stream()
                .map(this::toCategoriaDTO)
                .toList();
    }

    private CategoriaDTO toCategoriaDTO(ConteoCategoria conteo) {
        return new CategoriaDTO(conteo.getCategoria(), conteo.getCantidadProcesados());
    }
}