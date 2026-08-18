package com.api.techmind_g9_team34.api_techmind.exception;

/**
 * Se lanza cuando falla la extracción de título/texto desde un archivo
 * o URL: parser determinístico y fallback de Gemini fallan ambos, o la
 * respuesta de Gemini es inutilizable.
 */
public class ExtraccionException extends RuntimeException {
    public ExtraccionException(String message) {
        super(message);
    }

    public ExtraccionException(String message, Throwable cause) {
        super(message, cause);
    }
}
