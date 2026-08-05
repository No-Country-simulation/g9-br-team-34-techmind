package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.dto.response.CategoriaDTO;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.CategoriaService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * TM-038 — Implementación de {@link CategoriaService}.
 *
 * <p>En TM-038 la {@code cantidadProcesados} va en {@code null} (se omite del
 * JSON); TM-067 la puebla con el conteo real agrupado.
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
        return contenidoRepository.findCategoriasDistintas().stream()
                .map(c -> new CategoriaDTO(c, null))
                .toList();
    }
}