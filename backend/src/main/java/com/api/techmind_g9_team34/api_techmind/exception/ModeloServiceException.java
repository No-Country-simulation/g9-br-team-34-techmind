package com.api.techmind_g9_team34.api_techmind.exception;

public class ModeloServiceException extends RuntimeException {
    public ModeloServiceException(String message) {
        super(message);
    }

    public ModeloServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}