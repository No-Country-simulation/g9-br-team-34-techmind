package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.impl.ContenidoServiceImpl;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ContenidoServiceImplTest {

    @Mock
    private ModeloInferenciaClient client;

    @Mock
    private ContenidoAnalizadoRepository repository;

    @Mock
    private Validator validator;

    private ContenidoMapper mapper;
    private ContenidoService service;

    @BeforeEach
    void setUp() {
        mapper = new ContenidoMapper();
        service = new ContenidoServiceImpl(client, mapper, repository, validator);
    }

    @Test
    void deberiaProcesarYPersistirContenidoConExito() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Introducción a Spring Boot",
                "En este contenido se presentan los conceptos básicos para la creación de APIs REST."
        );
        ModelPredictClientResponseDto clientResponse = new ModelPredictClientResponseDto(
                "Backend", 0.89, List.of("Java", "Spring Boot", "API REST")
        );
        given(client.predecir(any(ModelPredictClientRequestDto.class)))
                .willReturn(clientResponse);
        given(repository.save(any(ContenidoAnalizado.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        ContenidoResponseDTO response = service.procesarContenido(request);

        assertThat(response.titulo()).isEqualTo("Introducción a Spring Boot");
        assertThat(response.categoria()).isEqualTo("Backend");
        assertThat(response.probabilidad()).isEqualTo(0.89);
        assertThat(response.informacionAdicional())
                .containsExactly("Java", "Spring Boot", "API REST");
        verify(repository).save(any(ContenidoAnalizado.class));
    }

    @Test
    void deberiaLanzarModeloServiceExceptionCuandoElClienteFalla() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Título válido",
                "Texto con al menos veinte caracteres de longitud."
        );
        given(client.predecir(any(ModelPredictClientRequestDto.class)))
                .willThrow(new RuntimeException("Connection refused"));

        assertThatThrownBy(() -> service.procesarContenido(request))
                .isInstanceOf(ModeloServiceException.class)
                .hasMessageContaining("no está disponible");
    }

    @Test
    void deberiaDevolverContenidoCuandoExistePorId() {
        UUID id = UUID.randomUUID();
        ContenidoAnalizado entity = ContenidoAnalizado.builder()
                .id(id)
                .titulo("Título")
                .texto("Texto con más de veinte caracteres.")
                .categoria("Backend")
                .probabilidad(0.9)
                .palabrasClave(List.of("Java"))
                .build();
        given(repository.findById(id)).willReturn(Optional.of(entity));

        ContenidoResponseDTO response = service.obtenerContenido(id);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.titulo()).isEqualTo("Título");
    }

    @Test
    void deberiaLanzarContenidoNoEncontradoParaIdInexistente() {
        UUID id = UUID.randomUUID();
        given(repository.findById(id)).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.obtenerContenido(id))
                .isInstanceOf(com.api.techmind_g9_team34.api_techmind.exception.ContenidoNoEncontradoException.class);
    }
}
