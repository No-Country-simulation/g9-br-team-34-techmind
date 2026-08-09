package com.api.techmind_g9_team34.api_techmind.exception;

import com.api.techmind_g9_team34.api_techmind.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;

/**
 * Maneja de forma centralizada las excepciones de toda la API.
 *
 * Todas las respuestas de error se construyen utilizando
 * {@link ErrorResponseDTO} para mantener un formato uniforme.
 *
 * TM-020 - GlobalExceptionHandler base con @ControllerAdvice.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger =
            LoggerFactory.getLogger(GlobalExceptionHandler.class);

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

    @ExceptionHandler(ValidacionException.class)
    public ResponseEntity<ErrorResponseDTO> handleValidacion(
            ValidacionException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        ex.getMessage(),
                        request.getRequestURI()));
    }

    /**
     * Argumento de ruta o query con un tipo que no se puede convertir, p. ej. un
     * {@code id} que no es un UUID válido (TM-036).
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponseDTO> handleTypeMismatch(
            MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "El valor proporcionado no tiene el formato esperado.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(ContenidoNoEncontradoException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoEncontrado(
            ContenidoNoEncontradoException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDTO.of(
                        HttpStatus.NOT_FOUND,
                        ex.getMessage(),
                        request.getRequestURI()));
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

    @ExceptionHandler(ProcesamientoException.class)
    public ResponseEntity<ErrorResponseDTO> handleProcesamiento(
            ProcesamientoException ex, HttpServletRequest request) {
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponseDTO> handleMaxUploadSizeExceeded(
            MaxUploadSizeExceededException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.PAYLOAD_TOO_LARGE)
                .body(ErrorResponseDTO.of(
                        HttpStatus.PAYLOAD_TOO_LARGE,
                        "El archivo supera el tamaño máximo permitido.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponseDTO> handleGeneral(
            Exception ex, HttpServletRequest request) {

        logger.error(
                "Excepción no controlada al procesar la solicitud [{}]",
                request.getRequestURI(),
                ex);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErrorResponseDTO.of(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "Ocurrió un error inesperado.",
                        request.getRequestURI()));
    }
}