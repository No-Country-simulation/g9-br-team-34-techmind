package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoLoteResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.FilaResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException;
import com.api.techmind_g9_team34.api_techmind.exception.ValidacionException;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import java.util.UUID;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Service
public class ContenidoServiceImpl implements ContenidoService {

    @Value("${techmind.csv.max-rows:100}")
    private int maxRows;

    private final ModeloInferenciaClient modeloClient;
    private final ContenidoMapper mapper;
    private final ContenidoAnalizadoRepository contenidoRepository;
    private final Validator validator;

    public ContenidoServiceImpl(
            ModeloInferenciaClient modeloClient,
            ContenidoMapper mapper,
            ContenidoAnalizadoRepository contenidoRepository,
            Validator validator) {

        this.modeloClient = modeloClient;
        this.mapper = mapper;
        this.contenidoRepository = contenidoRepository;
        this.validator = validator;
    }

    @Override
    @Transactional
    public ContenidoResponseDTO procesarContenido(ContenidoRequestDTO request) {
        ModelPredictClientRequestDto clientRequest = mapper.toClientRequest(request);
        ModelPredictClientResponseDto clientResponse;
        try {
            clientResponse = modeloClient.predecir(clientRequest);
        } catch (Exception e) {
            throw new ModeloServiceException(
                    "El servicio de análisis no está disponible en este momento.", e);
        }
        ContenidoAnalizado entity = mapper.toEntity(request, clientResponse);
        ContenidoAnalizado persistido = contenidoRepository.save(entity);
        return mapper.toResponseDTO(persistido);
    }

    @Override
    @Transactional(readOnly = true)
    public ContenidoResponseDTO obtenerContenido(UUID id) {
        ContenidoAnalizado entity = contenidoRepository.findById(id)
                .orElseThrow(() -> new ContenidoNoEncontradoException(
                        "No existe un contenido procesado con el id indicado."));
        return mapper.toResponseDTO(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public PaginaDTO<ContenidoResumenDTO> listarContenidos(
            String categoria, String palabraClave, Pageable pageable) {

        Specification<ContenidoAnalizado> spec =
                combinar(porCategoria(categoria), porPalabraClave(palabraClave));

        Page<ContenidoAnalizado> page = contenidoRepository.findAll(spec, pageable);
        return PaginaDTO.de(page.map(mapper::toResumenDTO));
    }

    /**
     * Combina Specifications con AND lógico, tolerando {@code null}.
     *
     * <p>Spring Data {@code Specification.and()} no acepta {@code this == null},
     * así que se combina de forma defensiva: si una de las dos es {@code null},
     * se devuelve la otra; si ambas lo son, se devuelve {@code null} (sin
     * filtro).
     */
    private Specification<ContenidoAnalizado> combinar(
            Specification<ContenidoAnalizado> a, Specification<ContenidoAnalizado> b) {
        if (a == null) {
            return b;
        }
        if (b == null) {
            return a;
        }
        return a.and(b);
    }

    /**
     * Specification de filtro exacto por categoría.
     *
     * <p>Si el parámetro es nulo o blank, devuelve {@code null} (sin filtro) para
     * que {@code findAll(null, pageable)} no aplique restricción. Las categorías
     * las produce el modelo y se asumen canónicas, por eso la comparación no es
     * insensible a mayúsculas (FR-016).
     *
     * @param categoria categoría a filtrar (opcional)
     * @return Specification o {@code null} si no hay filtro
     */
    private Specification<ContenidoAnalizado> porCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("categoria"), categoria);
    }

    /**
     * Specification de búsqueda por palabra clave sobre {@code titulo} y
     * {@code texto}, insensible a mayúsculas (TM-050).
     *
     * <p>Usa {@code lower()} en ambos lados de un {@code LIKE} para que el
     * matcheo no dependa del casing, y hace OR entre los dos campos: el
     * contenido coincide si la palabra aparece en cualquiera de ellos. Si el
     * parámetro es nulo o blank, devuelve {@code null} (sin filtro).
     *
     * @param palabraClave término a buscar (opcional)
     * @return Specification o {@code null} si no hay filtro
     */
    private Specification<ContenidoAnalizado> porPalabraClave(String palabraClave) {
        if (palabraClave == null || palabraClave.isBlank()) {
            return null;
        }
        String patron = "%" + palabraClave.toLowerCase() + "%";
        return (root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("titulo")), patron),
                cb.like(cb.lower(root.get("texto")), patron)
        );
    }

    @Override
    public ContenidoLoteResultadoDTO procesarLote(MultipartFile archivo) {

        List<FilaResultadoDTO> resultados = new ArrayList<>();

        int totalFilas = 0;
        int procesadosExitosos = 0;
        int procesadosConError = 0;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(
                        archivo.getInputStream(),
                        StandardCharsets.UTF_8))) {

            String encabezado = reader.readLine();

            if (encabezado == null) {
                throw new ValidacionException(
                        "El archivo CSV está vacío.");
            }

            String[] columnasEncabezado = encabezado.split(",", -1);

            if (columnasEncabezado.length != 2
                    || !columnasEncabezado[0].trim().equalsIgnoreCase("titulo")
                    || !columnasEncabezado[1].trim().equalsIgnoreCase("texto")) {

                throw new ValidacionException(
                        "El archivo CSV debe contener el encabezado: titulo,texto.");
            }

            List<String> lineas = new ArrayList<>();
            String linea;
            while ((linea = reader.readLine()) != null) {
                lineas.add(linea);
            }

            if (lineas.size() > maxRows) {
                throw new ValidacionException(
                        "El archivo supera el máximo permitido de "
                                + maxRows
                                + " filas.");
            }

            totalFilas = lineas.size();
            int filas = 0;

            for (String filaLinea : lineas) {

                filas++;

                String[] columnas = filaLinea.split(",", 2);

                if (columnas.length < 2) {
                    resultados.add(new FilaResultadoDTO(
                            filas,
                            "ERROR",
                            null,
                            "Fila CSV inválida"
                    ));
                    procesadosConError++;
                    continue;
                }

                try {
                    ContenidoRequestDTO request =
                            new ContenidoRequestDTO(
                                    columnas[0].trim(),
                                    columnas[1].trim());

                    Set<ConstraintViolation<ContenidoRequestDTO>> errores =
                            validator.validate(request);

                    if (!errores.isEmpty()) {
                        resultados.add(new FilaResultadoDTO(
                                filas,
                                "ERROR",
                                null,
                                "No fue posible procesar la fila"
                        ));
                        procesadosConError++;
                        continue;
                    }

                    ContenidoResponseDTO resultado = procesarContenido(request);

                    resultados.add(new FilaResultadoDTO(
                            filas,
                            "PROCESADO",
                            resultado,
                            null
                    ));
                    procesadosExitosos++;

                } catch (Exception e) {
                    resultados.add(new FilaResultadoDTO(
                            filas,
                            "ERROR",
                            null,
                            "No fue posible procesar la fila"
                    ));
                    procesadosConError++;
                }
            }

        } catch (IOException e) {
            throw new ValidacionException(
                    "No fue posible leer el archivo CSV.");
        }

        return new ContenidoLoteResultadoDTO(
                totalFilas,
                procesadosExitosos,
                procesadosConError,
                resultados
        );
    }

    /**
     * TM-051 — Elimina un contenido procesado.
     *
     * <p>Se comprueba la existencia con {@code existsById} antes de borrar, en
     * lugar de llamar a {@code deleteById} directamente: ese método no falla si
     * el id no existe, de modo que sin la comprobación previa la API devolvería
     * 204 tanto al borrar algo real como al pedir el borrado de un id
     * inventado, y el cliente no podría distinguir un caso del otro.
     *
     * <p>Se usa {@code existsById} y no {@code findById} porque no hace falta
     * traer la entidad ni sus palabras clave para decidir: alcanza con un
     * {@code count} en base.
     */
    @Override
    @Transactional
    public void eliminarContenido(UUID id) {
        if (!contenidoRepository.existsById(id)) {
            throw new ContenidoNoEncontradoException(
                    "No existe un contenido procesado con el id indicado.");
        }
        contenidoRepository.deleteById(id);
    }
}