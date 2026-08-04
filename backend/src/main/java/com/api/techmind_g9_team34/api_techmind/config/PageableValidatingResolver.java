package com.api.techmind_g9_team34.api_techmind.config;

import com.api.techmind_g9_team34.api_techmind.exception.ValidacionException;
import org.springframework.core.MethodParameter;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import java.util.List;

/**
 * TM-062 — Resoutor de {@link Pageable} con validación.
 *
 * <p>Sustituye al resolutor por defecto de Spring Data para rechazar peticiones
 * cuyo {@code size} exceda el máximo permitido o cuyo {@code sort} no esté en la
 * lista blanca {@code {fechaProcesamiento, titulo, categoria}} (RNF-01 / SC-006).
 * Un {@code page} o {@code size} negativo también se rechaza con 400.
 */
public class PageableValidatingResolver implements HandlerMethodArgumentResolver {

    public static final int MIN_PAGE = 0;
    public static final int MAX_SIZE = 50;
    public static final int DEFAULT_SIZE = 20;

    private static final List<String> SORT_WHITELIST =
            List.of("fechaProcesamiento", "titulo", "categoria");
    private static final List<String> SORT_DIRECTIONS =
            List.of("asc", "desc");

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return Pageable.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest,
            WebDataBinderFactory binderFactory) {

        int page = parsePage(webRequest.getParameter("page"));
        int size = parseSize(webRequest.getParameter("size"));
        Sort sort = parseSort(webRequest.getParameter("sort"));

        return PageRequest.of(page, size, sort);
    }

    private int parsePage(String valor) {
        if (!StringUtils.hasText(valor)) {
            return MIN_PAGE;
        }
        int page;
        try {
            page = Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new ValidacionException("El parámetro 'page' debe ser un número entero.");
        }
        if (page < MIN_PAGE) {
            throw new ValidacionException("El parámetro 'page' debe ser mayor o igual a 0.");
        }
        return page;
    }

    private int parseSize(String valor) {
        if (!StringUtils.hasText(valor)) {
            return DEFAULT_SIZE;
        }
        int size;
        try {
            size = Integer.parseInt(valor);
        } catch (NumberFormatException e) {
            throw new ValidacionException("El parámetro 'size' debe ser un número entero.");
        }
        if (size < 1) {
            throw new ValidacionException("El parámetro 'size' debe ser mayor o igual a 1.");
        }
        if (size > MAX_SIZE) {
            throw new ValidacionException(
                    "El parámetro 'size' no puede exceder " + MAX_SIZE + ".");
        }
        return size;
    }

    private Sort parseSort(String valor) {
        if (!StringUtils.hasText(valor)) {
            return Sort.unsorted();
        }
        String[] partes = valor.split(",");
        String propiedad = partes[0].trim();
        if (!SORT_WHITELIST.contains(propiedad)) {
            throw new ValidacionException(
                    "El parámetro 'sort' solo permite las propiedades: " + String.join(", ", SORT_WHITELIST) + ".");
        }
        String direccion = partes.length > 1 ? partes[1].trim() : "asc";
        if (!SORT_DIRECTIONS.contains(direccion.toLowerCase())) {
            throw new ValidacionException("El parámetro 'sort' solo admite dirección 'asc' o 'desc'.");
        }
        return Sort.by(Sort.Direction.fromString(direccion), propiedad);
    }
}