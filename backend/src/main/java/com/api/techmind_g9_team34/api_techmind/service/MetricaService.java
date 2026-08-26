package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.response.MetricasDTO;

/**
 * S5-14 — Agregaciones para el tablero de métricas.
 */
public interface MetricaService {

    /**
     * Calcula el tablero completo.
     *
     * <p>Con la base vacía devuelve un tablero en ceros con las listas vacías,
     * nunca nulos ni excepción: un repositorio sin contenidos es un estado
     * válido del producto, no un error.
     *
     * @return las métricas derivables de lo que hay persistido (ver S5-15)
     */
    MetricasDTO obtenerMetricas();
}
