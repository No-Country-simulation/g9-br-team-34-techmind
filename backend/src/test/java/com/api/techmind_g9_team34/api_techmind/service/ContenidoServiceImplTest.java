package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import com.api.techmind_g9_team34.api_techmind.mapper.ContenidoMapper;
import com.api.techmind_g9_team34.api_techmind.service.impl.ContenidoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ContenidoServiceImplTest {

    @Mock
    private ModeloInferenciaClient client;

    private ContenidoMapper mapper;
    private ContenidoService service;

    @BeforeEach
    void setUp() {
        mapper = new ContenidoMapper();
        service = new ContenidoServiceImpl(client, mapper);
    }

    @Test
    void deberiaProcesarContenidoConExito() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Introducción a Spring Boot",
                "En este contenido se presentan los conceptos básicos para la creación de APIs REST."
        );
        ModelPredictClientResponseDto clientResponse = new ModelPredictClientResponseDto(
                "Backend", 0.89, List.of("Java", "Spring Boot", "API REST")
        );
        given(client.predecir(any(ModelPredictClientRequestDto.class)))
                .willReturn(clientResponse);

        ContenidoResponseDTO response = service.procesarContenido(request);

        assertThat(response.titulo()).isEqualTo("Introducción a Spring Boot");
        assertThat(response.categoria()).isEqualTo("Backend");
        assertThat(response.probabilidad()).isEqualTo(0.89);
        assertThat(response.informacionAdicional())
                .containsExactly("Java", "Spring Boot", "API REST");
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
}