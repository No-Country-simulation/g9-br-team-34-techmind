package com.api.techmind_g9_team34.api_techmind.repository.projection;

/**
 * S5-14 — Confianza media del modelo dentro de una categoría.
 *
 * <p>Es la métrica más accionable del tablero: un promedio global alto puede
 * esconder una categoría donde el clasificador dude sistemáticamente.
 */
public interface ConfianzaCategoria {

    String getCategoria();

    /** Media de {@code probabilidad} en la categoría, en el rango [0, 1]. */
    Double getConfianzaMedia();

    /** Cantidad de contenidos sobre la que se calculó la media. */
    long getCantidad();
}
