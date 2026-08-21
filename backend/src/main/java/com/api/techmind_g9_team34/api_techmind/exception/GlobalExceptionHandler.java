package com.api.techmind_g9_team34.api_techmind.exception;

import com.api.techmind_g9_team34.api_techmind.dto.response.ErrorResponseDTO;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Maneja de forma centralizada las excepciones de toda la API.
 *
 * Todas las respuestas de error se construyen utilizando
 * {@link ErrorResponseDTO} para mantener un formato uniforme.
 *
 * TM-020 - GlobalExceptionHandler base con @ControllerAdvice.
 *
 * <p>S5-01 — Cada respuesta suma {@code mensajeUsuario} y {@code sugerencia}.
 * El texto que la interfaz muestra ya no sale de {@code message}, que queda
 * reservado para registros y depuración.
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
                        "Algunos datos no cumplen con lo que la API espera.",
                        "Revisá los campos señalados y volvé a enviarlo.",
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
                        // Estas excepciones ya se lanzan con texto pensado para
                        // quien usa la API, así que se reutiliza tal cual.
                        ex.getMessage(),
                        "Corregí el valor y volvé a intentarlo.",
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
                        "El identificador de la dirección no tiene el formato correcto.",
                        "Volvé al listado y entrá de nuevo al contenido.",
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
                        "No encontramos ese contenido.",
                        "Puede haber sido eliminado. Volvé al listado para ver los disponibles.",
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
                        "No pudimos analizar este contenido.",
                        "Probá con un texto más largo o con más variedad de palabras.",
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
                        "El contenido llegó bien, pero no se pudo procesar.",
                        "Revisá que el texto tenga contenido técnico reconocible.",
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
                        "El servicio que clasifica los contenidos no está disponible.",
                        "Es temporal. Esperá un momento y volvé a intentarlo.",
                        request.getRequestURI()));
    }

    @ExceptionHandler(ExtraccionException.class)
    public ResponseEntity<ErrorResponseDTO> handleExtraccion(
            ExtraccionException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ErrorResponseDTO.of(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        ex.getMessage(),
                        "No pudimos extraer el texto del archivo o la página.",
                        "Verificá que el documento tenga texto seleccionable; los PDF escaneados todavía no se pueden leer.",
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
                        "El archivo pesa demasiado.",
                        "Probá con un documento más liviano o subilo por partes.",
                        request.getRequestURI()));
    }

    /**
     * Solicitud no multipart o sin el campo {@code archivo} en el lote
     * ({@code POST /contenidos/lote}). Sin esto respondía 500 en lugar de 400
     * (detectado en pruebas de aceptación de TM-052).
     */
    @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class})
    public ResponseEntity<ErrorResponseDTO> handleMultipartAusente(
            Exception ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "La solicitud debe incluir el campo 'archivo' en formato multipart/form-data.",
                        "No se pudo leer el archivo enviado.",
                        "Seleccioná el archivo de nuevo y volvé a enviarlo.",
                        request.getRequestURI()));
    }

    /**
     * {@code Content-Type} que el endpoint no soporta, p. ej. un POST JSON
     * enviado como {@code text/plain}. La especificación (Sección 4.1) exige
     * 400 y no 415 para este caso; sin este handler respondía 500.
     */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponseDTO> handleMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex, HttpServletRequest request) {
        return ResponseEntity
                .badRequest()
                .body(ErrorResponseDTO.of(
                        HttpStatus.BAD_REQUEST,
                        "El Content-Type de la solicitud debe ser application/json.",
                        "El formato de la solicitud no es el esperado.",
                        request.getRequestURI()));
    }

    /**
     * Recurso estático o ruta sin controlador, p. ej. el {@code favicon.ico}
     * que los navegadores piden solos o una URL inexistente.
     *
     * <p>Sin este handler, la excepción cae en el catch-all
     * ({@link #handleGeneral}) y la API responde 500 con stack trace en el log.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponseDTO> handleNoResource(
            NoResourceFoundException ex, HttpServletRequest request) {
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ErrorResponseDTO.of(
                        HttpStatus.NOT_FOUND,
                        "El recurso solicitado no existe.",
                        "La dirección a la que entraste no existe.",
                        "Volvé al inicio.",
                        request.getRequestURI()));
    }

    /**
     * Cualquier fallo no contemplado (TM-034).
     *
     * <p>{@code message} lleva un texto fijo y no {@code ex.getMessage()}: el
     * mensaje de una excepción no controlada puede contener rutas de archivos,
     * nombres de tablas o direcciones de servicios internos. La traza completa
     * se registra con nivel ERROR y no sale nunca en la respuesta.
     */
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
                        "Algo salió mal de nuestro lado.",
                        "Ya quedó registrado. Volvé a intentarlo en unos minutos.",
                        request.getRequestURI()));
    }
}