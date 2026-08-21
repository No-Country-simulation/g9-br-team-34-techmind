package com.api.techmind_g9_team34.api_techmind.mapper;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ContenidoMapperTest {

    private final ContenidoMapper mapper = new ContenidoMapper();

    @Test
    void deberiaMapearRequestAClientRequest() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Título prueba", "Texto de prueba con más de veinte caracteres."
        );
        ModelPredictClientRequestDto clientRequest = mapper.toClientRequest(request);
        assertThat(clientRequest.titulo()).isEqualTo("Título prueba");
        assertThat(clientRequest.texto()).isEqualTo("Texto de prueba con más de veinte caracteres.");
    }

    @Test
    void deberiaMapearClientResponseAResponse() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Título original", "Texto con más de veinte caracteres."
        );
        ModelPredictClientResponseDto clientResponse = new ModelPredictClientResponseDto(
                "Backend", 0.95, List.of("Java", "API")
        );
        ContenidoResponseDTO response = mapper.toResponse(request, clientResponse);
        assertThat(response.titulo()).isEqualTo("Título original");
        assertThat(response.texto()).isEqualTo("Texto con más de veinte caracteres.");
        assertThat(response.categoria()).isEqualTo("Backend");
        assertThat(response.probabilidad()).isEqualTo(0.95);
        assertThat(response.informacionAdicional()).containsExactly("Java", "API");
        assertThat(response.id()).isNull();
        assertThat(response.resumen()).isNull();
        assertThat(response.fechaProcesamiento()).isNull();
    }

    @Test
    void deberiaMapearAmodeloYClientResponseAEntidadSinIdNiFecha() {
        ContenidoRequestDTO request = new ContenidoRequestDTO(
                "Título original", "Texto con más de veinte caracteres."
        );
        ModelPredictClientResponseDto clientResponse = new ModelPredictClientResponseDto(
                "DevOps", 0.80, List.of("Docker", "Kubernetes")
        );

        ContenidoAnalizado entity = mapper.toEntity(request, clientResponse, "Resumen de prueba.");

        assertThat(entity.getTitulo()).isEqualTo("Título original");
        assertThat(entity.getTexto()).isEqualTo("Texto con más de veinte caracteres.");
        assertThat(entity.getResumen()).isEqualTo("Resumen de prueba.");
        assertThat(entity.getCategoria()).isEqualTo("DevOps");
        assertThat(entity.getProbabilidad()).isEqualTo(0.80);
        assertThat(entity.getPalabrasClave()).containsExactly("Docker", "Kubernetes");
        assertThat(entity.getId()).isNull();
        assertThat(entity.getFechaProcesamiento()).isNull();
    }

    @Test
    void deberiaMapearEntidadAResponseDTO() {
        UUID id = UUID.randomUUID();
        Instant fecha = Instant.parse("2026-08-04T15:30:00Z");
        ContenidoAnalizado entity = ContenidoAnalizado.builder()
                .id(id)
                .titulo("Título")
                .texto("Texto con más de veinte caracteres.")
                .resumen("Resumen de prueba.")
                .categoria("Backend")
                .probabilidad(0.9)
                .palabrasClave(List.of("Java"))
                .fechaProcesamiento(fecha)
                .build();

        ContenidoResponseDTO response = mapper.toResponseDTO(entity);

        assertThat(response.id()).isEqualTo(id);
        assertThat(response.titulo()).isEqualTo("Título");
        assertThat(response.texto()).isEqualTo("Texto con más de veinte caracteres.");
        assertThat(response.resumen()).isEqualTo("Resumen de prueba.");
        assertThat(response.categoria()).isEqualTo("Backend");
        assertThat(response.probabilidad()).isEqualTo(0.9);
        assertThat(response.informacionAdicional()).containsExactly("Java");
        assertThat(response.fechaProcesamiento()).isEqualTo(fecha);
    }
}