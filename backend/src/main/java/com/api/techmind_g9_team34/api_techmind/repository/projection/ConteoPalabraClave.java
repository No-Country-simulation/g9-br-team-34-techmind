package com.api.techmind_g9_team34.api_techmind.repository.projection;

/**
 * S5-14 — Frecuencia de una palabra clave en todo el repositorio.
 *
 * <p>Cuenta en cuántos contenidos aparece el término, no cuántas veces aparece
 * dentro de cada texto: la colección guarda un valor por contenido.
 */
public interface ConteoPalabraClave {

    String getPalabraClave();

    long getCantidad();
}
