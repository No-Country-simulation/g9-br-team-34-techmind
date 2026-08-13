package com.api.techmind_g9_team34.api_techmind.client;

/**
 * Cliente para el fallback/limpieza de extracción vía Gemini.
 *
 * <p>Se usa en dos escenarios (ver ExtraccionArchivoService):
 * <ul>
 *   <li>Extracción directa desde archivo, cuando el parser determinístico
 *       falló por completo (ej. PDF escaneado sin texto seleccionable)</li>
 *   <li>Limpieza de título/texto ya extraído, quitando ruido como
 *       páginas de edición/autor, menús de navegación, índices, etc.</li>
 * </ul>
 */
public interface GeminiExtractionClient {

    /**
     * Envía un archivo (PDF, PNG, JPG) codificado y pide a Gemini que
     * extraiga título y texto directamente desde su contenido.
     *
     * @return el texto crudo de la respuesta de Gemini (aún sin parsear
     *         a título/texto — eso lo hace ExtraccionArchivoService)
     */
    String extraerDeArchivo(byte[] contenido, String mimeType);

    /**
     * Envía título/texto ya extraído por un parser determinístico para
     * que Gemini lo limpie, quitando ruido sin resumir ni parafrasear.
     *
     * @return el texto crudo de la respuesta de Gemini
     */
    String limpiar(String tituloCrudo, String textoCrudo);
}
