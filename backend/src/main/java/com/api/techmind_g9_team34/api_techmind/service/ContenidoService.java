package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLoteResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface ContenidoService {

    ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request);

    ContenidoLoteResultadoDTO procesarLote(MultipartFile archivo);

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

}