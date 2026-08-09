package com.api.techmind_g9_team34.api_techmind.dto.response;

import org.springframework.data.domain.Page;

import java.util.List;

/**
 * TM-037 — Wrapper de paginación simplificado.
 *
 * <p>Representa una página de resultados de forma plana, sin exponer el
 * {@link Page} verboso de Spring Data (no incluye {@code Pageable} ni
 * {@code Sort} internos).
 *
 * @param <T> tipo de los elementos de la página
 */
public record PaginaDTO<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages
) {

    /**
     * Construye un {@code PaginaDTO} a partir de un {@link Page} de Spring Data.
     *
     * @param page página de Spring Data
     * @param <T>  tipo de los elementos
     * @return wrapper simplificado
     */
    public static <T> PaginaDTO<T> de(Page<T> page) {
        return new PaginaDTO<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}