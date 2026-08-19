package com.api.techmind_g9_team34.api_techmind.service.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.List;

/**
 * Extrae título y texto de contenido HTML, ya sea desde bytes de un
 * archivo local o directamente desde una URL (ej. una consulta de
 * foro en vivo).
 *
 * <p>Extracción en dos capas:
 * <ol>
 *   <li>Se quitan tags que casi nunca son contenido real ({@code <nav>},
 *       {@code <header>}, {@code <footer>}, {@code <aside>},
 *       {@code <script>}, {@code <style>}) — genérico, cualquier sitio.</li>
 *   <li>Se intenta ubicar el contenedor de contenido principal (ej.
 *       {@code #mw-content-text} de MediaWiki/Wikipedia, o
 *       {@code <article>}/{@code <main>} en sitios más modernos) y se usa
 *       SOLO su texto — así, cualquier resto de navegación/buscador/
 *       sidebar que no esté en los tags de la capa 1 (ej. skip-links o
 *       cajas de búsqueda sueltas en {@code <div>}s) queda afuera de raíz,
 *       sin depender de identificar cada elemento de ruido uno por uno.</li>
 * </ol>
 * Si ningún selector de contenido principal aplica, se cae al texto de
 * todo el {@code <body>} ya filtrado por la capa 1 — sigue siendo mejor
 * que no filtrar nada, aunque no sea perfecto para sitios desconocidos.
 */
@Service
public class HtmlParserService {

    private static final int TIMEOUT_MS = 10_000;
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    /** Tags que casi nunca contienen el contenido real, sin importar el sitio. */
    private static final String SELECTOR_RUIDO = "script, style, nav, header, footer, aside";

    /**
     * Contenedores de contenido principal conocidos, en orden de prioridad.
     * {@code #mw-content-text} es específico de MediaWiki (Wikipedia), que
     * es una fuente de datos ya usada en el proyecto (ver pipeline de
     * Data Science) — no es una apuesta genérica, es un sitio real que
     * vamos a recibir seguido. {@code article}/{@code main}/{@code [role=main]}
     * cubren sitios modernos en general.
     */
    private static final List<String> SELECTORES_CONTENIDO_PRINCIPAL = List.of(
            "#mw-content-text",
            "article",
            "main",
            "[role=main]"
    );

    public record ResultadoExtraccion(String titulo, String texto) {
    }

    /** Parsea HTML ya descargado (bytes de un archivo local). */
    public ResultadoExtraccion extraerDesdeContenido(byte[] contenido) {
        Document soup = Jsoup.parse(new String(contenido), "");
        return procesarDocumento(soup);
    }

    /**
     * Descarga una página (ej. un hilo de foro) y extrae título y texto.
     * Lanza IOException si la URL falla (timeout, 404, etc.) — el
     * llamador (ExtraccionArchivoServiceImpl) decide cómo manejarlo.
     */
    public ResultadoExtraccion extraerDesdeUrl(String url) throws IOException {
        Document soup = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get();

        return procesarDocumento(soup);
    }

    private ResultadoExtraccion procesarDocumento(Document soup) {
        // El título se extrae ANTES de eliminar nada, por si el <h1>
        // viviera dentro de alguno de los tags que vamos a quitar
        // (poco común, pero es una salvaguarda barata).
        String titulo = obtenerTitulo(soup);

        soup.select(SELECTOR_RUIDO).remove();
        String texto = extraerTextoContenidoPrincipal(soup);

        return new ResultadoExtraccion(titulo, texto.strip());
    }

    /**
     * Intenta encontrar un contenedor de contenido principal; si ninguno
     * aplica o queda vacío, cae al texto de todo el body ya filtrado.
     */
    private String extraerTextoContenidoPrincipal(Document soup) {
        for (String selector : SELECTORES_CONTENIDO_PRINCIPAL) {
            Element candidato = soup.selectFirst(selector);
            if (candidato != null && !candidato.text().isBlank()) {
                return candidato.text();
            }
        }

        return soup.body() != null ? soup.body().text() : soup.text();
    }

    private String obtenerTitulo(Document soup) {
        if (!soup.title().isBlank()) {
            return soup.title().strip();
        }

        Element h1 = soup.selectFirst("h1");
        if (h1 != null && !h1.text().isBlank()) {
            return h1.text().strip();
        }

        return "Sin título";
    }
}