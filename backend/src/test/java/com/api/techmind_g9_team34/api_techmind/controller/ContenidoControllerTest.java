package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.client.ModeloInferenciaClient;
import com.api.techmind_g9_team34.api_techmind.dto.client.ModelPredictClientResponseDto;
import com.api.techmind_g9_team34.api_techmind.exception.ModeloServiceException;
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

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

    // ---------- TM-049 / TM-068: GET /{id}/relacionados ----------

    @Test
    void deberiaDevolverRelacionadosDeLaMismaCategoriaOrdenadosPorCoincidencias() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java", "spring", "jpa"));
        ContenidoAnalizado tresCoincidencias =
                conPalabras("Tres", "Backend", List.of("java", "spring", "jpa"));
        ContenidoAnalizado unaCoincidencia =
                conPalabras("Una", "Backend", List.of("java", "kotlin"));

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                // El de más palabras en común va primero.
                .andExpect(jsonPath("$[0].id").value(tresCoincidencias.getId().toString()))
                .andExpect(jsonPath("$[1].id").value(unaCoincidencia.getId().toString()));
    }

    @Test
    void noDeberiaIncluirElContenidoBaseEntreSusRelacionados() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java", "spring"));
        conPalabras("Otro", "Backend", List.of("java"));

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == '" + base.getId() + "')]", hasSize(0)));
    }

    @Test
    void noDeberiaDevolverContenidosDeOtraCategoria() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java", "spring"));
        conPalabras("Otra categoría", "Frontend", List.of("java", "spring"));

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deberiaDevolverListaVaciaSiNoHaySimilares() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java"));
        conPalabras("Sin nada en común", "Backend", List.of("python", "django"));

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void deberiaDevolver404SiElContenidoBaseNoExiste() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deberiaDevolver400EnRelacionadosSiElIdNoEsUuid() throws Exception {
        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void deberiaDevolverComoMaximo5RelacionadosSinParametroLimite() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java"));
        for (int i = 0; i < 8; i++) {
            conPalabras("Relacionado " + i, "Backend", List.of("java"));
        }

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(5)));
    }

    @Test
    void deberiaRespetarElParametroLimiteCuandoEsMenorAlMaximo() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java"));
        for (int i = 0; i < 8; i++) {
            conPalabras("Relacionado " + i, "Backend", List.of("java"));
        }

        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId())
                        .param("limite", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(3)));
    }

    @Test
    void deberiaAcotarA20UnLimiteMayorAlMaximo() throws Exception {
        ContenidoAnalizado base = conPalabras("Base", "Backend", List.of("java"));
        for (int i = 0; i < 25; i++) {
            conPalabras("Relacionado " + i, "Backend", List.of("java"));
        }

        // Decisión de TM-068: se acota a 20 en lugar de rechazar con 400.
        mockMvc.perform(get("/api/v1/contenidos/{id}/relacionados", base.getId())
                        .param("limite", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)));
    }

    // ---------- TM-044: servicio de inferencia caído ----------

    @Test
    void deberiaDevolver503CuandoElServicioDeInferenciaFalla() throws Exception {
        when(modeloInferenciaClient.predecir(any()))
                .thenThrow(new ModeloServiceException(
                        "El servicio de inferencia no está disponible."));

        String body = """
                {"titulo":"Introducción a Spring Boot",
                 "texto":"Contenido técnico con más de veinte caracteres para pasar la validación."}
                """;

        mockMvc.perform(post("/api/v1/contenidos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503));
    }

    private ContenidoAnalizado conPalabras(String titulo, String categoria, List<String> palabras) {
        return repository.save(ContenidoAnalizado.builder()
                .titulo(titulo)
                .texto("Texto de prueba con más de veinte caracteres válidos.")
                .categoria(categoria)
                .probabilidad(0.9)
                .palabrasClave(palabras)
                .build());
    }

    // ---------- TM-051: DELETE /api/v1/contenidos/{id} ----------

    @Test
    void deberiaDevolver204YEliminarElContenidoExistente() throws Exception {
        ContenidoAnalizado entity = contenido("Guía de Spring Data JPA", "Backend", 0.91);
        UUID id = entity.getId();

        mockMvc.perform(delete("/api/v1/contenidos/{id}", id))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        // El 204 por sí solo no prueba que la fila se haya borrado: se verifica
        // contra el repositorio, que es la única fuente de verdad.
        assertThat(repository.findById(id)).isEmpty();
    }

    @Test
    void deberiaDevolver404AlEliminarUnIdInexistente() throws Exception {
        UUID idInexistente = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/contenidos/{id}", idInexistente))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void deberiaDevolver400AlEliminarConUnIdQueNoEsUuid() throws Exception {
        mockMvc.perform(delete("/api/v1/contenidos/{id}", "no-es-un-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void noDeberiaEliminarOtrosContenidosAlBorrarUno() throws Exception {
        ContenidoAnalizado aBorrar = contenido("Contenido a borrar", "Backend", 0.90);
        ContenidoAnalizado aConservar = contenido("Contenido a conservar", "Frontend", 0.80);

        mockMvc.perform(delete("/api/v1/contenidos/{id}", aBorrar.getId()))
                .andExpect(status().isNoContent());

        // Protege contra un deleteAll accidental o un borrado por criterio erróneo.
        assertThat(repository.findById(aConservar.getId())).isPresent();
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