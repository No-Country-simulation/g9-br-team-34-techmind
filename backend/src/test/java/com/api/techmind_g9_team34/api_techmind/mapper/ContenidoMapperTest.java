package com.api.techmind_g9_team34.api_techmind.mapper;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.dto.response.ContenidoResponseDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertThat(response.categoria()).isEqualTo("Backend");
        assertThat(response.probabilidad()).isEqualTo(0.95);
        assertThat(response.informacionAdicional()).containsExactly("Java", "API");
        assertThat(response.id()).isNull();
        assertThat(response.fechaProcesamiento()).isNull();
    }
}