package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoUrlRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLoteResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLotePdfResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ExtraccionException;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import com.api.techmind_g9_team34.api_techmind.service.ExtraccionArchivoService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/contenidos")
public class ContenidoController {

    private static final Logger logger =
            LoggerFactory.getLogger(ContenidoController.class);

    private final ContenidoService contenidoService;
    private final ExtraccionArchivoService extraccionArchivoService;

    public ContenidoController(
            ContenidoService contenidoService,
            ExtraccionArchivoService extraccionArchivoService) {
        this.contenidoService = contenidoService;
        this.extraccionArchivoService = extraccionArchivoService;
    }

    @PostMapping
    public ResponseEntity<ContenidoResponseDTO> procesarContenido(
            @Valid @RequestBody ContenidoRequestDTO request,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoResponseDTO response =
                contenidoService.procesarContenido(request);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/lote")
    public ResponseEntity<ContenidoLoteResultadoDTO> procesarLote(
            @RequestParam("archivo") MultipartFile archivo) {

        ContenidoLoteResultadoDTO response =
                contenidoService.procesarLote(archivo);

        return ResponseEntity.ok(response);
    }

    /**
     * Procesa un lote de PDFs en una sola operación (S5-09). Cada archivo
     * se extrae (PDFBox + fallback/limpieza Gemini) y clasifica de forma
     * independiente: si uno falla, los demás se siguen procesando, y el
     * detalle de cada uno queda en la respuesta.
     */
    @PostMapping("/lote-pdf")
    public ResponseEntity<ContenidoLotePdfResultadoDTO> procesarLotePdf(
            @RequestParam("archivos") List<MultipartFile> archivos) {

        ContenidoLotePdfResultadoDTO response =
                contenidoService.procesarLotePdf(archivos);

        return ResponseEntity.ok(response);
    }

    /**
     * Procesa un archivo técnico (PDF o DOCX): extrae título/texto y lo
     * limpia vía Gemini (ver ExtraccionArchivoService), luego sigue el
     * mismo flujo que POST /api/v1/contenidos.
     */
    @PostMapping(value = "/archivo")
    public ResponseEntity<ContenidoResponseDTO> procesarDesdeArchivo(
            @RequestParam("archivo") MultipartFile archivo,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoRequestDTO datosExtraidos = leerYExtraer(archivo);

        ContenidoResponseDTO response =
                contenidoService.procesarContenido(datosExtraidos);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/v1/contenidos/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    /**
     * Procesa contenido accesible por URL (ej. una consulta de foro):
     * extrae título/texto y lo limpia vía Gemini, luego sigue el mismo
     * flujo que POST /api/v1/contenidos.
     */
    @PostMapping("/url")
    public ResponseEntity<ContenidoResponseDTO> procesarDesdeUrl(
            @Valid @RequestBody ContenidoUrlRequestDTO request,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoRequestDTO datosExtraidos =
                extraccionArchivoService.extraerDesdeUrl(request.url());

        ContenidoResponseDTO response =
                contenidoService.procesarContenido(datosExtraidos);

        URI location = ServletUriComponentsBuilder
                .fromCurrentRequest()
                .replacePath("/api/v1/contenidos/{id}")
                .buildAndExpand(response.id())
                .toUri();

        return ResponseEntity.created(location).body(response);
    }

    private ContenidoRequestDTO leerYExtraer(MultipartFile archivo) {
        try {
            return extraccionArchivoService.extraerDesdeArchivo(
                    archivo.getBytes(), archivo.getOriginalFilename());
        } catch (IOException e) {
            throw new ExtraccionException("No se pudo leer el archivo subido.", e);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ContenidoResponseDTO> obtenerContenido(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        ContenidoResponseDTO response =
                contenidoService.obtenerContenido(id);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<PaginaDTO<ContenidoResumenDTO>> listarContenidos(
            @RequestParam(name = "categoria", required = false) String categoria,
            @RequestParam(name = "palabraClave", required = false) String palabraClave,
            Pageable pageable,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        PaginaDTO<ContenidoResumenDTO> response =
                contenidoService.listarContenidos(categoria, palabraClave, pageable);

        return ResponseEntity.ok(response);
    }

    /**
     * TM-051 — Elimina un contenido procesado.
     *
     * <p>Devuelve 204 sin cuerpo al borrar, 404 si el id no existe y 400 si el
     * path variable no tiene formato UUID (esto último lo resuelve
     * {@code GlobalExceptionHandler} vía {@code MethodArgumentTypeMismatchException},
     * TM-064).
     */
    /**
     * TM-049 y TM-068 — Contenidos relacionados a uno dado.
     *
     * <p>Devuelve 200 con la lista (posiblemente vacía si no hay similares),
     * 404 si el contenido base no existe y 400 si el path variable no tiene
     * formato UUID — este último lo resuelve {@code GlobalExceptionHandler} vía
     * {@code MethodArgumentTypeMismatchException} (TM-064), sin llegar al
     * repositorio.
     *
     * <p>{@code limite} es opcional: ausente equivale a 5 y los valores mayores
     * a 20 se acotan a 20 en lugar de rechazarse (decisión de TM-068,
     * documentada en {@code ContenidoService}).
     */
    @GetMapping("/{id}/relacionados")
    public ResponseEntity<List<ContenidoResumenDTO>> obtenerRelacionados(
            @PathVariable UUID id,
            @RequestParam(name = "limite", required = false) Integer limite,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        List<ContenidoResumenDTO> response =
                contenidoService.obtenerRelacionados(id, limite);

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminarContenido(
            @PathVariable UUID id,
            HttpServletRequest httpRequest) {

        logger.info("Solicitud recibida para {}", httpRequest.getRequestURI());

        contenidoService.eliminarContenido(id);

        return ResponseEntity.noContent().build();
    }
}