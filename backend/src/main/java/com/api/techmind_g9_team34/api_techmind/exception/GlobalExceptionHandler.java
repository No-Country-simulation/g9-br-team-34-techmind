package com.api.techmind_g9_team34.api_techmind.exception;

import com.api.techmind_g9_team34.api_techmind.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.util.List;

@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidation(
            MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ErrorResponseDTO.CampoInvalido> errores = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> new ErrorResponseDTO.CampoInvalido(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ErrorResponseDTO.deValidacion(
                        "Error de validación en los campos de la solicitud.",
                        request.getRequestURI(),
                        errores));
    }

    @ExceptionHandler(ContenidoNoProcesableException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoProcesable(
            ContenidoNoProcesableException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponseDTO.of(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(ModeloServiceException.class)
    public ResponseEntity<ErrorResponseDTO> handleModeloService(
            ModeloServiceException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ErrorResponseDTO.of(
                        HttpStatus.SERVICE_UNAVAILABLE,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneral(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDTO.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ocurrió un error inesperado.",
                        request.getRequestURI()));
    }
}
