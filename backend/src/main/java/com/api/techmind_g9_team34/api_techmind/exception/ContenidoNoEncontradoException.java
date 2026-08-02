package com.api.techmind_g9_team34.api_techmind.exception;

/**
 * Excepción lanzada cuando no existe un contenido solicitado.
 *
 * TM-022 - Respuesta 404 para contenido inexistente.
 */
public class ContenidoNoEncontradoException extends RuntimeException {

    public ContenidoNoEncontradoException(String message) {
        super(message);
    }
}