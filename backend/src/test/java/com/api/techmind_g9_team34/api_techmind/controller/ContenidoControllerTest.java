package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "techmind.oci.enabled=false")
class ContenidoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContenidoAnalizadoRepository repository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ModeloInferenciaClient modeloInferenciaClient;

    @BeforeEach
    void configurarMock() {
        when(modeloInferenciaClient.predecir(any()))
                .thenReturn(new ModelPredictClientResponseDto(
                        "Backend", 0.89, List.of("Java", "Spring Boot", "API REST")));
    }

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    @Test
    void deberiaResponder201ConIdYLocationAlProcesar() throws Exception {
        mockMvc.perform(post("/api/v1/contenidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Introducción a Spring Boot",
                                 "texto": "En este contenido se presentan las bases para crear APIs REST con Java y Spring Boot."}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.categoria").value("Backend"))
                .andExpect(jsonPath("$.informacion_adicional[0]").value("Java"));
    }

    @Test
    void deberiaPersistirLaFilaEnBase() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/contenidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"titulo": "Persistencia",
                                 "texto": "Este contenido tiene un texto válido de más de veinte caracteres."}
                                """))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        UUID id = UUID.fromString(body.get("id").asText());
        var existente = repository.findById(id);
        org.assertj.core.api.Assertions.assertThat(existente).isPresent();
    }

    @Test
    void deberiaDevolver200ConContenidoCompletoPorId() throws Exception {
        ContenidoAnalizado entity = repository.save(ContenidoAnalizado.builder()
                .titulo("Título")
                .texto("Texto con más de veinte caracteres.")
                .categoria("Backend")
                .probabilidad(0.95)
                .palabrasClave(List.of("Java", "SPRING"))
                .build());
        UUID id = entity.getId();

        mockMvc.perform(get("/api/v1/contenidos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.titulo").value("Título"))
                .andExpect(jsonPath("$.categoria").value("Backend"))
                .andExpect(jsonPath("$.probabilidad").value(0.95))
                .andExpect(jsonPath("$.informacion_adicional[0]").value("Java"));
    }

    @Test
    void deberiaDevolver404ParaIdInexistente() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deberiaDevolver400ParaIdQueNoEsUuid() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos/no-es-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    void deberiaListarContenidosSinFiltroConResumenDTO() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);
        contenido("Docker en producción", "DevOps", 0.8);
        contenido("Bases de datos relacionales", "Backend", 0.7);

        mockMvc.perform(get("/api/v1/contenidos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.content[*].id").isNotEmpty())
                .andExpect(jsonPath("$.content[*].categoria").value(
                        org.hamcrest.Matchers.hasItems("Backend", "DevOps")))
                .andExpect(jsonPath("$.content[0].fechaProcesamiento").isNotEmpty())
                .andExpect(jsonPath("$.page").value(0));
    }

    @Test
    void deberiaFiltrarPorCategoria() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);
        contenido("Docker en producción", "DevOps", 0.8);
        contenido("Bases de datos relacionales", "Backend", 0.7);

        mockMvc.perform(get("/api/v1/contenidos").param("categoria", "Backend"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.content[*].categoria").value(
                        org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.is("Backend"))))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void deberiaDevolverListaVaciaParaCategoriaInexistente() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);

        mockMvc.perform(get("/api/v1/contenidos").param("categoria", "Inexistente"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void deberiaBuscarPorPalabraClaveInsensibleAMayusculas() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);
        contenido("Docker en producción", "DevOps", 0.8,
                "Guía rápida sobre contenedores con spring y docker compose.");
        contenido("Bases de datos relacionales", "Backend", 0.7);

        mockMvc.perform(get("/api/v1/contenidos").param("palabraClave", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void deberiaCombinarCategoriaYPalabraClaveConAnd() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);
        contenido("Integración continua", "Backend", 0.85,
                "Pipeline con despliegue automatizado a un entorno de pruebas.");
        contenido("Docker en producción", "DevOps", 0.8);

        mockMvc.perform(get("/api/v1/contenidos")
                        .param("categoria", "Backend")
                        .param("palabraClave", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].titulo").value("Introducción a Spring"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    void deberiaIgnorarElParametroKeywordAntiguo() throws Exception {
        contenido("Introducción a Spring", "Backend", 0.9);
        contenido("Docker en producción", "DevOps", 0.8,
                "Guía rápida sobre contenedores con docker compose.");
        contenido("Bases de datos relacionales", "Backend", 0.7);

        mockMvc.perform(get("/api/v1/contenidos").param("keyword", "spring"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.totalElements").value(3));
    }

    @Test
    void deberiaPaginacionConTituloTotalElementsYTotalPages() throws Exception {
        for (int i = 1; i <= 12; i++) {
            contenido("Titulo " + i, "Backend", 0.5);
        }

        mockMvc.perform(get("/api/v1/contenidos")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(10))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(12))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    void deberiaRechazarSizeMayorA50() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos").param("size", "51"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deberiaRechazarSortNoPermitido() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos").param("sort", "probabilidad,desc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void deberiaOrdenarPorTituloAscendente() throws Exception {
        contenido("Zeta", "Backend", 0.5);
        contenido("Alfa", "Backend", 0.5);
        contenido("Beta", "Backend", 0.5);

        mockMvc.perform(get("/api/v1/contenidos").param("sort", "titulo,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].titulo").value("Alfa"));
    }

    @Test
    void deberiaRechazarPageOSizeNegativos() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos").param("page", "-1"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/contenidos").param("size", "-5"))
                .andExpect(status().isBadRequest());
    }

    private ContenidoAnalizado contenido(String titulo, String categoria, double probabilidad) {
        return contenido(titulo, categoria, probabilidad,
                "Texto de prueba con más de veinte caracteres válidos.");
    }

    private ContenidoAnalizado contenido(String titulo, String categoria, double probabilidad, String texto) {
        return repository.save(ContenidoAnalizado.builder()
                .titulo(titulo)
                .texto(texto)
                .categoria(categoria)
                .probabilidad(probabilidad)
                .palabrasClave(List.of("Java"))
                .build());
    }
}