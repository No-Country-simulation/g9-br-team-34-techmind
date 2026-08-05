package com.api.techmind_g9_team34.api_techmind.repository.projection;

/**
 * TM-067 — Proyección de conteo de contenidos agrupado por categoría.
 *
 * <p>Spring Data mapea una {@code @Query} a este interface por convención de
 * propiedades: es obligatorio que los getters se llamen igual que los alias
 * del select ({@code categoria}, {@code cantidadProcesados}).
 */
public interface ConteoCategoria {

    String getCategoria();

    long getCantidadProcesados();
}