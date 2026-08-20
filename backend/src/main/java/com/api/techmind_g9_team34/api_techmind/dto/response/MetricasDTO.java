package com.api.techmind_g9_team34.api_techmind.dto.response;

import java.time.Instant;
import java.util.List;

/**
 * S5-14 — Tablero completo de métricas del repositorio.
 *
 * <p>Se devuelve todo en una sola respuesta y no en un endpoint por métrica
 * porque el dashboard (S5-16) las muestra juntas: separarlas obligaría a seis
 * peticiones para pintar una pantalla.
 *
 * <p>Ninguna cifra llega nula. Con la base vacía, {@code avg} devuelve
 * {@code null} en SQL; el servicio lo convierte a cero antes de armar este
 * DTO, para que el frontend no tenga que defenderse de ausencias.
 *
 * <p>El alcance sale de S5-15 (spike): sólo entran métricas derivables de lo
 * que ya se persiste, sin campos nuevos ni registro de eventos.
 */
public record MetricasDTO(

        /** M1 — Contenidos analizados en total. */
        long totalContenidos,

        /** M2 — Categorías distintas con al menos un contenido. */
        long totalCategorias,

        /** M3 — Confianza media del modelo, en el rango [0, 1]. */
        double confianzaMedia,

        /** M8 — Longitud media del texto analizado, en caracteres. */
        double longitudMediaTexto,

        /** M9 — Palabras clave que el modelo extrae por contenido, en promedio. */
        double palabrasClavePorContenido,

        /** M4 — Confianza por categoría, de la más dudosa a la más firme. */
        List<ConfianzaPorCategoria> confianzaPorCategoria,

        /** M5 — Cuántos contenidos caen en cada tramo de confianza. */
        List<TramoConfianza> distribucionConfianza,

        /** M6 — Contenidos procesados por día, del más viejo al más reciente. */
        List<ProcesadosPorDia> procesadosPorDia,

        /** M7 — Palabras clave más frecuentes del repositorio. */
        List<PalabraClaveFrecuente> palabrasClaveTop,

        /** Momento en que se calculó este tablero. */
        Instant calculadoEn

) {

    /** M4 — Una categoría con su confianza media y cuántos contenidos la sostienen. */
    public record ConfianzaPorCategoria(
            String categoria,
            double confianzaMedia,
            long cantidad) {
    }

    /**
     * M5 — Un tramo del histograma de confianza.
     *
     * @param etiqueta texto legible del rango, ya formateado para mostrar
     * @param desde    límite inferior, inclusive, en [0, 1]
     * @param hasta    límite superior, exclusive, en [0, 1]
     * @param cantidad contenidos que caen dentro
     */
    public record TramoConfianza(
            String etiqueta,
            double desde,
            double hasta,
            long cantidad) {
    }

    /**
     * M6 — Contenidos procesados en un día.
     *
     * @param fecha    día en formato ISO (yyyy-MM-dd), en UTC
     * @param cantidad contenidos procesados ese día
     */
    public record ProcesadosPorDia(
            String fecha,
            long cantidad) {
    }

    /** M7 — Una palabra clave y en cuántos contenidos aparece. */
    public record PalabraClaveFrecuente(
            String palabraClave,
            long cantidad) {
    }
}
