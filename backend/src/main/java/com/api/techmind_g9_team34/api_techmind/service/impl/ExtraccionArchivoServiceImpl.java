package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.client.GeminiExtractionClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.GeminiExtraccionParseadaDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ExtraccionException;
import com.api.techmind_g9_team34.api_techmind.service.ExtraccionArchivoService;
import com.api.techmind_g9_team34.api_techmind.service.parser.DocxParserService;
import com.api.techmind_g9_team34.api_techmind.service.parser.HtmlParserService;
import com.api.techmind_g9_team34.api_techmind.service.parser.PdfParserService;
import com.api.techmind_g9_team34.api_techmind.util.Constants;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Implementación de {@link ExtraccionArchivoService}.
 *
 * <p>Flujo en dos pasos, aplicado SIEMPRE (no solo en casos de error):
 * <ol>
 *   <li><b>Extracción básica</b> vía parsers determinísticos (PDFBox,
 *       POI, jsoup) — rápida y gratis, pero "sucia": incluye páginas de
 *       edición/autor en libros, menús en HTML, índices, etc.</li>
 *   <li><b>Limpieza vía Gemini</b> — recibe el título/texto crudo y
 *       devuelve solo el contenido relevante para clasificación.</li>
 * </ol>
 *
 * <p>Solo se salta el paso 1 cuando el parser determinístico falla por
 * completo o no produce texto suficiente — ahí Gemini extrae
 * directamente desde el archivo en vez de limpiar algo que no existe.
 */
@Service
public class ExtraccionArchivoServiceImpl implements ExtraccionArchivoService {

    private static final Logger logger = LoggerFactory.getLogger(ExtraccionArchivoServiceImpl.class);

    /**
     * Captura el valor de "titulo" sin depender de que el resto del JSON
     * sea válido. Non-greedy hasta la primera comilla sin escapar antes
     * de la coma que introduce "texto". Cubre el caso típico donde el
     * JSON se rompe más adelante (en "texto"), pero "titulo" ya quedó
     * completo al inicio de la respuesta.
     */
    private static final Pattern PATRON_TITULO =
            Pattern.compile("\"titulo\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");

    private final PdfParserService pdfParser;
    private final DocxParserService docxParser;
    private final HtmlParserService htmlParser;
    private final GeminiExtractionClient geminiClient;
    private final ObjectMapper objectMapper;

    public ExtraccionArchivoServiceImpl(
            PdfParserService pdfParser,
            DocxParserService docxParser,
            HtmlParserService htmlParser,
            GeminiExtractionClient geminiClient,
            ObjectMapper objectMapper
    ) {
        this.pdfParser = pdfParser;
        this.docxParser = docxParser;
        this.htmlParser = htmlParser;
        this.geminiClient = geminiClient;
        this.objectMapper = objectMapper;
    }

    @Override
    public ContenidoRequestDTO extraerDesdeArchivo(byte[] contenido, String nombreArchivo) {
        String extension = obtenerExtension(nombreArchivo);

        String tituloCrudo;
        String textoCrudo;

        try {
            switch (extension) {
                case ".pdf" -> {
                    var resultado = pdfParser.extraer(contenido);
                    tituloCrudo = resultado.titulo();
                    textoCrudo = resultado.texto();

                    // Carátula/portada como imagen: el documento completo
                    // puede tener texto de sobra, pero si la página 1 casi
                    // no tiene texto seleccionable, el título probablemente
                    // está incrustado en una imagen que PDFBox no puede
                    // leer. En ese caso, mandamos el PDF completo (con
                    // imágenes) a Gemini en vez de solo el texto crudo, así
                    // su visión puede leer el título directamente.
                    if (resultado.caracteresPrimeraPagina() < Constants.MIN_CARACTERES_PRIMERA_PAGINA) {
                        logger.warn("Primera página de {} con poco texto seleccionable ({} caracteres) — " +
                                        "posible carátula con título como imagen. Usando Gemini con visión.",
                                nombreArchivo, resultado.caracteresPrimeraPagina());
                        return extraerDirectoDesdeArchivo(contenido, extension);
                    }
                }
                case ".docx" -> {
                    var resultado = docxParser.extraer(contenido);
                    tituloCrudo = resultado.titulo();
                    textoCrudo = resultado.texto();
                }
                default -> throw new ExtraccionException("Extensión de archivo no soportada: " + extension);
            }
        } catch (ExtraccionException e) {
            throw e;
        } catch (Exception e) {
            logger.warn("Extracción determinística falló para {}: {}. Extrayendo directo desde el archivo vía Gemini.",
                    nombreArchivo, e.getMessage());
            return extraerDirectoDesdeArchivo(contenido, extension);
        }

        if (textoCrudo == null || textoCrudo.length() < Constants.MIN_CARACTERES_VALIDOS) {
            logger.warn("Extracción pobre para {} ({} caracteres). Extrayendo directo desde el archivo vía Gemini.",
                    nombreArchivo, textoCrudo == null ? 0 : textoCrudo.length());
            return extraerDirectoDesdeArchivo(contenido, extension);
        }

        return limpiarConGemini(tituloCrudo, textoCrudo);
    }

    @Override
    public ContenidoRequestDTO extraerDesdeUrl(String url) {
        try {
            var resultado = htmlParser.extraerDesdeUrl(url);

            if (resultado.texto() == null || resultado.texto().length() < Constants.MIN_CARACTERES_VALIDOS) {
                logger.warn("Extracción pobre para URL {}.", url);
                throw new ExtraccionException("No se pudo extraer contenido suficiente de la URL: " + url);
            }

            return limpiarConGemini(resultado.titulo(), resultado.texto());

        } catch (IOException e) {
            logger.warn("No se pudo descargar/parsear la URL {}: {}.", url, e.getMessage());
            throw new ExtraccionException("No se pudo acceder a la URL proporcionada: " + url, e);
        }
    }

    private ContenidoRequestDTO limpiarConGemini(String tituloCrudo, String textoCrudo) {
        String respuestaCruda = geminiClient.limpiar(tituloCrudo, textoCrudo);
        return parsearRespuestaGemini(respuestaCruda);
    }

    private ContenidoRequestDTO extraerDirectoDesdeArchivo(byte[] contenido, String extension) {
        String mimeType = switch (extension) {
            case ".pdf" -> "application/pdf";
            case ".png" -> "image/png";
            case ".jpg", ".jpeg" -> "image/jpeg";
            default -> throw new ExtraccionException("Gemini no soporta extraer directamente: " + extension);
        };

        String respuestaCruda = geminiClient.extraerDeArchivo(contenido, mimeType);
        return parsearRespuestaGemini(respuestaCruda);
    }

    private ContenidoRequestDTO parsearRespuestaGemini(String respuestaCruda) {
        String limpio = extraerJson(respuestaCruda);

        try {
            GeminiExtraccionParseadaDto parseado = objectMapper.readValue(limpio, GeminiExtraccionParseadaDto.class);
            return new ContenidoRequestDTO(
                    truncar(parseado.titulo(), Constants.MAX_CARACTERES_TITULO),
                    truncar(parseado.texto(), Constants.MAX_CARACTERES_TEXTO));
        } catch (IOException e) {
            String fragmento = limpio.length() > 300 ? limpio.substring(0, 300) + "..." : limpio;
            logger.warn("Gemini no devolvió JSON válido, usando extracción tolerante como fallback. " +
                    "Primeros caracteres de la respuesta: {}", fragmento);

            String titulo = extraerTituloTolerante(limpio);
            return new ContenidoRequestDTO(titulo, truncar(limpio, Constants.MAX_CARACTERES_TEXTO));
        }
    }

    /**
     * Cuando el JSON completo no es válido, el punto de quiebre casi
     * siempre está en el campo "texto" (es el más largo, y por lo tanto
     * el más propenso a truncarse por límite de tokens o a llevar algún
     * carácter sin escapar). El campo "titulo" es corto y aparece antes
     * en la respuesta, así que casi siempre está completo y bien formado
     * aunque el resto del JSON no lo esté — por eso vale la pena
     * intentar extraerlo con una expresión regular tolerante en vez de
     * resignarnos directamente a "Sin título".
     */
    private String extraerTituloTolerante(String textoConJsonRoto) {
        Matcher matcher = PATRON_TITULO.matcher(textoConJsonRoto);

        if (matcher.find()) {
            String titulo = matcher.group(1)
                    .replace("\\\"", "\"")
                    .replace("\\n", " ")
                    .strip();

            if (!titulo.isEmpty()) {
                return titulo;
            }
        }

        return "Sin título";
    }

    /**
     * Extrae el objeto JSON de la respuesta de Gemini de forma defensiva:
     * quita marcadores de markdown (```json ... ```) si los hay, y luego
     * recorta al primer '{' y al último '}' — así, si el modelo agrega
     * texto explicativo antes o después del JSON (algo que puede pasar
     * pese a las instrucciones del prompt), igual se puede parsear.
     */
    private String extraerJson(String respuestaCruda) {
        String sinFences = respuestaCruda.replace("```json", "").replace("```", "").strip();

        int inicio = sinFences.indexOf('{');
        int fin = sinFences.lastIndexOf('}');

        if (inicio == -1 || fin == -1 || fin < inicio) {
            return sinFences;
        }

        return sinFences.substring(inicio, fin + 1);
    }

    /**
     * Corta el texto al máximo indicado si lo supera, sin lanzar error.
     * Ver Javadoc de las constantes MAX_CARACTERES_* para el porqué.
     */
    private String truncar(String texto, int maximo) {
        if (texto == null || texto.length() <= maximo) {
            return texto;
        }
        logger.info("Campo extraído ({} caracteres) supera el máximo permitido ({}); se trunca.",
                texto.length(), maximo);
        return texto.substring(0, maximo);
    }

    private String obtenerExtension(String nombreArchivo) {
        if (nombreArchivo == null) {
            return "";
        }
        int idx = nombreArchivo.lastIndexOf('.');
        return idx >= 0 ? nombreArchivo.substring(idx).toLowerCase() : "";
    }
}