package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;

/**
 * Extrae título y texto desde archivos (PDF, DOCX)
 * o URLs (consultas de foro), devolviendo el resultado ya limpio en
 * el mismo contrato que espera el resto del pipeline de contenidos.
 */
public interface ExtraccionArchivoService {

    /**
     * Extrae y limpia título/texto desde un archivo subido.
     *
     * @param contenido      bytes del archivo
     * @param nombreArchivo  nombre original (se usa para inferir la extensión)
     */
    ContenidoRequestDTO extraerDesdeArchivo(byte[] contenido, String nombreArchivo);

    /**
     * Extrae y limpia título/texto desde una URL (ej. una consulta de foro).
     */
    ContenidoRequestDTO extraerDesdeUrl(String url);
}
