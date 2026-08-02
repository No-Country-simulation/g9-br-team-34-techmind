package com.api.techmind_g9_team34.api_techmind.client;

import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientRequestDto;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class RestModeloInferenciaClientTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestBodyUriSpec requestBodyUriSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec responseSpec;

    private RestModeloInferenciaClient client;

    @BeforeEach
    void setUp() {
        client = new RestModeloInferenciaClient(restClient, "http://localhost:8000");
        given(restClient.post()).willReturn(requestBodyUriSpec);
        given(requestBodyUriSpec.uri("http://localhost:8000/predict")).willReturn(requestBodySpec);
    }

    @Test
    void deberiaDevolverRespuestaCuandoElServicioRespondeOk() {
        ModelPredictClientRequestDto request = new ModelPredictClientRequestDto("Título", "Texto con al menos veinte caracteres.");
        ModelPredictClientResponseDto expected = new ModelPredictClientResponseDto("Backend", 0.89, List.of("Java"));

        given(requestBodySpec.body(request)).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve()).willReturn(responseSpec);
        given(responseSpec.body(ModelPredictClientResponseDto.class)).willReturn(expected);

        ModelPredictClientResponseDto result = client.predecir(request);

        assertThat(result.categoria()).isEqualTo("Backend");
        assertThat(result.probabilidad()).isEqualTo(0.89);
        assertThat(result.informacionAdicional()).containsExactly("Java");
    }

    @Test
    void deberiaReintentarUnaVezAnteFalloTransitorioYExito() {
        ModelPredictClientRequestDto request = new ModelPredictClientRequestDto("Título", "Texto con al menos veinte caracteres.");
        ModelPredictClientResponseDto expected = new ModelPredictClientResponseDto("Backend", 0.89, List.of());

        given(requestBodySpec.body(request)).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve())
                .willThrow(new ResourceAccessException("timeout", new ConnectException("Connection refused")))
                .willReturn(responseSpec);
        given(responseSpec.body(ModelPredictClientResponseDto.class)).willReturn(expected);

        ModelPredictClientResponseDto result = client.predecir(request);

        assertThat(result.categoria()).isEqualTo("Backend");
        verify(requestBodySpec, times(2)).retrieve();
    }

    @Test
    void deberiaFallarConModeloServiceExceptionCuandoSeAgotaElReintento() {
        ModelPredictClientRequestDto request = new ModelPredictClientRequestDto("Título", "Texto con al menos veinte caracteres.");

        given(requestBodySpec.body(request)).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve())
                .willThrow(new ResourceAccessException("timeout", new ConnectException("Connection refused")));

        assertThatThrownBy(() -> client.predecir(request))
                .isInstanceOf(ModeloServiceException.class)
                .hasMessageContaining("no está disponible");
        verify(requestBodySpec, times(2)).retrieve();
    }

    @Test
    void noDeberiaReintentarAnteErrorHttpDelServicio() {
        ModelPredictClientRequestDto request = new ModelPredictClientRequestDto("Título", "Texto con al menos veinte caracteres.");

        given(requestBodySpec.body(request)).willReturn(requestBodySpec);
        given(requestBodySpec.retrieve())
                .willThrow(new HttpServerErrorException(HttpStatus.INTERNAL_SERVER_ERROR));

        assertThatThrownBy(() -> client.predecir(request))
                .isInstanceOf(ModeloServiceException.class);
        verify(requestBodySpec, times(1)).retrieve();
    }
}