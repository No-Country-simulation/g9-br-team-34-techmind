package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLotePdfResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLoteResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

public interface ContenidoService {

    ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request);

    ContenidoLoteResultadoDTO procesarLote(MultipartFile archivo);

     /**
     * Procesa un lote de archivos PDF en una sola operación (S5-09).
     *
     * <p>Reutiliza {@code ExtraccionArchivoService} (extracción con
     * PDFBox + limpieza/fallback con Gemini) y luego el mismo
     * {@link #procesarContenido(ContenidoRequestDTO)} que usa el
     * endpoint individual — el lote es, en esencia, N llamadas a ese
     * mismo camino, con manejo de errores por archivo.
     *
     * <p>Cada archivo se procesa de forma independiente: si uno falla
     * (parser y fallback de Gemini agotados, o el resultado no pasa
     * validación), los demás se siguen procesando igual, y el detalle
     * del error de ese archivo específico se reporta en su
     * {@link com.api.techmind_g9_team34.api_techmind.dto.response.ArchivoResultadoDTO}
     * — no se aborta el lote completo por un solo archivo problemático.
     *
     * @param archivos lista de archivos PDF subidos en la misma request
     * @return resultado agregado con el detalle de cada archivo
     * @throws com.api.techmind_g9_team34.api_techmind.exception.ValidacionException
     *         si la lista está vacía, contiene un archivo que no es
     *         PDF, o excede el máximo de archivos permitido por lote
     */
    ContenidoLotePdfResultadoDTO procesarLotePdf(List<MultipartFile> archivos);

    /**
     * Obtiene un contenido previamente procesado por su id.
     *
     * @param id identificador del contenido
     * @return el contenido procesado
     * @throws com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException
     *         si no existe un contenido con ese id
     */
    ContenidoResponseDTO obtenerContenido(UUID id);

    /**
     * Lista contenidos procesados con filtro opcional por categoría.
     *
     * <p>Firma invariante entre TM-037, TM-050, TM-063 y TM-062: los TMs
     * sucesivos sólo activan comportamiento, no reescriben la firma.
     *
     * @param categoria     filtro exacto por categoría (nulo/blank se ignora)
     * @param palabraClave  búsqueda por palabra clave (TM-050+; ignorada en TM-037)
     * @param pageable      paginación (TM-062 activa la real)
     * @return página de resúmenes de contenidos
     */
    PaginaDTO<ContenidoResumenDTO> listarContenidos(
            String categoria,
            String palabraClave,
            Pageable pageable);

    /**
     * Elimina un contenido previamente procesado.
     *
     * <p>TM-051. La operación no es idempotente por decisión de contrato: un id
     * inexistente produce 404 y no 204. Se elige informar al cliente que el
     * recurso no existía, en lugar de dejarlo creyendo que borró algo.
     *
     * <p>Al borrar la fila, Hibernate elimina también sus palabras clave en
     * {@code contenido_palabras_clave}: es una {@code @ElementCollection}, cuyo
     * ciclo de vida depende por completo de la entidad dueña. No hace falta
     * borrarlas a mano.
     *
     * @param id identificador del contenido a eliminar
     * @throws com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException
     *         si no existe un contenido con ese id
     */
    void eliminarContenido(UUID id);

    /**
     * Devuelve contenidos relacionados a uno dado.
     *
     * <p>TM-049 y TM-068. El criterio de relación es <b>misma categoría y al
     * menos una palabra clave compartida</b>, ordenado por cantidad de palabras
     * en común de mayor a menor. Es determinístico y no consulta al servicio de
     * inferencia: la especificación de la API deja el algoritmo a criterio del
     * equipo, y resolverlo en base evita depender de un endpoint de similitud
     * que Ciencia de Datos no expone.
     *
     * <p>El contenido base nunca aparece entre sus propios relacionados.
     *
     * <p>Sobre {@code limite} (TM-068): {@code null} equivale al valor por
     * defecto (5) y cualquier valor mayor al máximo se acota a 20 en lugar de
     * rechazarse con 400. Se elige acotar porque pedir "los 50 más parecidos"
     * es una intención razonable del cliente y no un error de uso; devolver los
     * 20 mejores lo satisface mejor que un error.
     *
     * @param id      contenido base
     * @param limite  cantidad máxima de resultados; nulo usa 5, mayor a 20 se acota a 20
     * @return relacionados del más al menos similar; lista vacía si no hay ninguno
     * @throws com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException
     *         si no existe un contenido con ese id
     */
    List<ContenidoResumenDTO> obtenerRelacionados(UUID id, Integer limite);

}