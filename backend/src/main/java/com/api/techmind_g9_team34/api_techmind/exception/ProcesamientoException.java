package com.api.techmind_g9_team34.api_techmind.exception;

/**
 * Excepción lanzada cuando el contenido es válido,
 * pero no puede ser procesado por el modelo.
 *
 * TM-033 - Respuesta 422 Unprocessable Entity.
 */
public class ProcesamientoException extends RuntimeException {

    public ProcesamientoException(String message) {
        super(message);
    }
}
