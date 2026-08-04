package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResumenDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.PaginaDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.ContenidoService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContenidoServiceImpl implements ContenidoService {

    private final ModeloInferenciaClient modeloClient;
    private final ContenidoMapper mapper;
    private final ContenidoAnalizadoRepository contenidoRepository;

    public ContenidoServiceImpl(
            ModeloInferenciaClient modeloClient,
            ContenidoMapper mapper,
            ContenidoAnalizadoRepository contenidoRepository) {
        this.modeloClient = modeloClient;
        this.mapper = mapper;
        this.contenidoRepository = contenidoRepository;
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

        // TM-037: la paginación real llega en TM-062; mientras tanto se trae
        // todo en una sola página para no acotar resultados por defecto.
        Pageable efectivo = PageRequest.of(0, Integer.MAX_VALUE);

        Page<ContenidoAnalizado> page = contenidoRepository.findAll(spec, efectivo);
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
}