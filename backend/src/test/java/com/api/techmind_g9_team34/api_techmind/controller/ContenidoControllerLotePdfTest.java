package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.client.GeminiExtractionClient;
import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.dto.request.ContenidoRequestDTO;
import com.api.techmind_g9_team34.api_techmind.exception.ExtraccionException;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.ExtraccionArchivoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests de integración de {@code POST /api/v1/contenidos/lote-pdf} (S5-09).
 *
 * <p>Cubre los tres escenarios pedidos en los criterios de aceptación:
 * lote 100% exitoso, lote mixto (algunos fallan, otros no), y lote que
 * excede el límite configurado de archivos.
 *
 * <p>Igual que en {@code ContenidoControllerExtraccionTest}, se mockea
 * {@link ExtraccionArchivoService} completo — el detalle de la
 * extracción/fallback ya se prueba aparte en
 * {@code ExtraccionArchivoServiceImplTest}. Aquí el foco es el
 * procesamiento independiente por archivo y el conteo agregado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "techmind.oci.enabled=false",
        "techmind.pdf-lote.max-archivos=3"
})
class ContenidoControllerLotePdfTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContenidoAnalizadoRepository repository;

    @MockBean
    private ExtraccionArchivoService extraccionArchivoService;

    @MockBean
    private ModeloInferenciaClient modeloInferenciaClient;

    @MockBean
    private GeminiExtractionClient geminiExtractionClient;

    @BeforeEach
    void configurarMocks() {
        given(modeloInferenciaClient.predecir(any()))
                .willReturn(new ModelPredictClientResponseDto(
                        "Backend", 0.89, List.of("Java", "Spring Boot")));
        given(geminiExtractionClient.resumir(anyString()))
                .willReturn("Resumen generado de prueba.");
    }

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    @Test
    void deberiaProcesarUnLote100PorCientoExitoso() throws Exception {
        given(extraccionArchivoService.extraerDesdeArchivo(any(), eq("uno.pdf")))
                .willReturn(new ContenidoRequestDTO("Título uno", "Texto válido con más de veinte caracteres."));
        given(extraccionArchivoService.extraerDesdeArchivo(any(), eq("dos.pdf")))
                .willReturn(new ContenidoRequestDTO("Título dos", "Texto válido con más de veinte caracteres."));

        MockMultipartFile uno = new MockMultipartFile("archivos", "uno.pdf", "application/pdf", "contenido1".getBytes());
        MockMultipartFile dos = new MockMultipartFile("archivos", "dos.pdf", "application/pdf", "contenido2".getBytes());

        mockMvc.perform(multipart("/api/v1/contenidos/lote-pdf").file(uno).file(dos))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArchivos").value(2))
                .andExpect(jsonPath("$.procesadosExitosos").value(2))
                .andExpect(jsonPath("$.procesadosConError").value(0))
                .andExpect(jsonPath("$.resultados[0].estado").value("PROCESADO"))
                .andExpect(jsonPath("$.resultados[0].nombreArchivo").value("uno.pdf"))
                .andExpect(jsonPath("$.resultados[0].resultado.categoria").value("Backend"))
                .andExpect(jsonPath("$.resultados[1].estado").value("PROCESADO"))
                .andExpect(jsonPath("$.resultados[1].nombreArchivo").value("dos.pdf"));
    }

    @Test
    void deberiaProcesarUnLoteMixtoSinAbortarPorUnArchivoConError() throws Exception {
        given(extraccionArchivoService.extraerDesdeArchivo(any(), eq("bueno.pdf")))
                .willReturn(new ContenidoRequestDTO("Título válido", "Texto válido con más de veinte caracteres."));
        willThrow(new ExtraccionException("No se pudo extraer contenido suficiente del archivo"))
                .given(extraccionArchivoService).extraerDesdeArchivo(any(), eq("malo.pdf"));

        MockMultipartFile bueno = new MockMultipartFile("archivos", "bueno.pdf", "application/pdf", "ok".getBytes());
        MockMultipartFile malo = new MockMultipartFile("archivos", "malo.pdf", "application/pdf", "x".getBytes());

        mockMvc.perform(multipart("/api/v1/contenidos/lote-pdf").file(bueno).file(malo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalArchivos").value(2))
                .andExpect(jsonPath("$.procesadosExitosos").value(1))
                .andExpect(jsonPath("$.procesadosConError").value(1))
                .andExpect(jsonPath("$.resultados[0].estado").value("PROCESADO"))
                .andExpect(jsonPath("$.resultados[1].estado").value("ERROR"))
                .andExpect(jsonPath("$.resultados[1].nombreArchivo").value("malo.pdf"))
                .andExpect(jsonPath("$.resultados[1].mensajeError").isNotEmpty())
                .andExpect(jsonPath("$.resultados[1].resultado").doesNotExist());
    }

    @Test
    void deberiaRechazarUnArchivoQueNoEsPdfDentroDelLoteSinAbortarLosDemas() throws Exception {
        given(extraccionArchivoService.extraerDesdeArchivo(any(), eq("valido.pdf")))
                .willReturn(new ContenidoRequestDTO("Título válido", "Texto válido con más de veinte caracteres."));

        MockMultipartFile valido = new MockMultipartFile("archivos", "valido.pdf", "application/pdf", "ok".getBytes());
        MockMultipartFile noEsPdf = new MockMultipartFile("archivos", "imagen.png", "image/png", "x".getBytes());

        mockMvc.perform(multipart("/api/v1/contenidos/lote-pdf").file(valido).file(noEsPdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesadosExitosos").value(1))
                .andExpect(jsonPath("$.procesadosConError").value(1))
                .andExpect(jsonPath("$.resultados[1].estado").value("ERROR"))
                .andExpect(jsonPath("$.resultados[1].mensajeError").value(
                        "El archivo debe ser un PDF (extensión .pdf)."));
    }

    @Test
    void deberiaRechazarElLoteCompletoSiExcedeElMaximoDeArchivos() throws Exception {
        // El límite se configuró a 3 vía @TestPropertySource para no
        // tener que subir 21 archivos reales en este test.
        MockMultipartFile uno = new MockMultipartFile("archivos", "uno.pdf", "application/pdf", "1".getBytes());
        MockMultipartFile dos = new MockMultipartFile("archivos", "dos.pdf", "application/pdf", "2".getBytes());
        MockMultipartFile tres = new MockMultipartFile("archivos", "tres.pdf", "application/pdf", "3".getBytes());
        MockMultipartFile cuatro = new MockMultipartFile("archivos", "cuatro.pdf", "application/pdf", "4".getBytes());

        mockMvc.perform(multipart("/api/v1/contenidos/lote-pdf")
                        .file(uno).file(dos).file(tres).file(cuatro))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(
                        org.hamcrest.Matchers.containsString("máximo permitido de 3 archivos")));
    }

    @Test
    void deberiaRechazarUnLoteVacio() throws Exception {
        mockMvc.perform(multipart("/api/v1/contenidos/lote-pdf"))
                .andExpect(status().isBadRequest());
    }
}
