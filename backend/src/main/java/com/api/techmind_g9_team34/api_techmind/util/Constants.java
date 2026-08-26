package com.api.techmind_g9_team34.api_techmind.util;

public class Constants {

    /**
     * Umbral mínimo de caracteres para considerar que la extracción
     * determinística (PDFBox/POI/jsoup) produjo algo utilizable.
     * Por debajo de esto, se asume que falló por completo (ej. PDF
     * escaneado sin texto seleccionable) y se usa extracción directa
     * desde el archivo vía Gemini, en vez de solo limpieza.
     */
    public static final int MIN_CARACTERES_VALIDOS = 50;

    /**
     * Umbral mínimo de caracteres específicamente en la PRIMERA página
     * de un PDF. Es una señal distinta a MIN_CARACTERES_VALIDOS (que mide
     * el documento completo): un documento puede tener texto de sobra en
     * total y aun así tener una carátula/portada que es una imagen (título
     * incrustado como diseño gráfico, no como texto seleccionable). Si la
     * página 1 tiene menos que esto, se activa el fallback de Gemini con
     * visión (manda el PDF completo, no solo el texto) para que pueda
     * leer el título directamente de la imagen de la carátula.
     */
    public static final int MIN_CARACTERES_PRIMERA_PAGINA = 20;

    /**
     * Límite máximo de caracteres del texto final, replicando el
     * {@code @Size(max = 10_000)} de {@code ContenidoRequestDTO} y el
     * {@code @Column(length = 10_000)} de la entidad {@code ContenidoAnalizado}.
     * Si el texto limpio por Gemini supera esto (típico en libros
     * académicos completos), se trunca aquí en vez de dejar que la
     * validación del DTO lo rechace más adelante — preferimos un texto
     * truncado a perder todo el trabajo de extracción por un error 400.
     */
    public static final int MAX_CARACTERES_TEXTO = 10_000;

    /**
     * Igual que {@link #MAX_CARACTERES_TEXTO} pero para el título,
     * replicando el límite de {@code titulo} en DTO/entidad. El prompt
     * de limpieza ya le pide a Gemini un título breve, pero se trunca
     * aquí también como salvaguarda.
     */
    public static final int MAX_CARACTERES_TITULO = 200;

    /**
     * Igual que {@link #MAX_CARACTERES_TEXTO}/{@link #MAX_CARACTERES_TITULO}
     * pero para el resumen, replicando el {@code length = 2000} de
     * {@code ContenidoAnalizado.resumen} (TM-05 / S5-05, valor provisorio
     * hasta confirmar el contrato definitivo).
     */
    public static final int MAX_CARACTERES_RESUMEN = 2000;

    /**
     * Prompt para cuando la extracción determinística falló por completo
     * y hay que extraer título/texto directamente desde el archivo.
     */
    public static final String PROMPT_EXTRACCION_GEMINI = """
            Extrae el título y el contenido completo de este documento.
            Responde ÚNICAMENTE con un JSON válido, sin texto adicional, \
            con este formato exacto:
            {"titulo": "...", "texto": "..."}""";

    /**
     * Prompt de limpieza: se usa SIEMPRE, sobre el título/texto que ya
     * extrajeron los parsers determinísticos. No resume ni parafrasea,
     * solo descarta ruido (ediciones, autor, menús, índices, etc.).
     */
    public static final String PROMPT_LIMPIEZA_GEMINI = """
            A continuación tienes el título y el texto crudo extraídos de un \
            documento técnico, guía, libro académico o consulta de foro.

            Tu tarea es LIMPIAR este contenido, quedándote únicamente con el \
            contenido relevante para que un modelo de clasificación de texto \
            lo procese. Elimina específicamente (si aparecen):
            - Páginas de edición, datos del autor, ISBN, derechos de autor
            - Índices o tablas de contenido
            - Numeración de páginas, encabezados o pies de página repetidos
            - Menús de navegación, enlaces de sidebar, publicidad, banners \
            de cookies/suscripción
            - Cualquier otro texto que no aporte al contenido técnico principal

            NO resumas ni parafrasees el contenido real: consérvalo tal cual, \
            solo quita el ruido descrito arriba. Si el título no es claro o \
            está vacío, infiere uno breve y descriptivo a partir del contenido.

            IMPORTANTE sobre longitud: el campo "texto" no debe superar \
            aproximadamente 10000 caracteres. Si el contenido limpio es más \
            largo que eso, incluye solo los primeros 10000 caracteres \
            relevantes (no es necesario que termine en un punto exacto, \
            un corte a mitad de oración está bien) — no intentes resumir \
            para que quepa todo, simplemente no continúes más allá de ese \
            límite aproximado.

            Responde ÚNICAMENTE con un JSON válido, sin texto adicional, \
            con este formato exacto:
            {"titulo": "...", "texto": "..."}""";

    /**
     * PROVISORIO — pendiente de revisión contra el criterio exacto de S5-05.
     * No confirmar longitud, tono ni formato de salida sin cotejar el issue.
     *
     * <p>Prompt de resumen: se usa sobre el texto ya limpio (post
     * PROMPT_LIMPIEZA_GEMINI) para generar el campo {@code resumen} de
     * {@code ContenidoAnalizado}.
     */
    public static final String PROMPT_RESUMEN_GEMINI = """
            Genera un resumen claro y conciso del siguiente contenido técnico.

            No inventes información que no esté en el texto original. \
            Conserva los conceptos técnicos, nombres propios y datos \
            relevantes. No agregues opiniones ni valoraciones.

            IMPORTANTE sobre longitud: el resumen no debe superar \
            aproximadamente 2000 caracteres.

            Responde ÚNICAMENTE con el texto del resumen, sin JSON, \
            sin comillas y sin texto adicional.""";
}