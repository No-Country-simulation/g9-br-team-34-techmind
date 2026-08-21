package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S5-14 — Integración de {@code GET /api/v1/metricas}.
 *
 * <p>Los datos de cada prueba se eligen para que el resultado esperado se pueda
 * calcular a mano: así, si una agregación cambia de comportamiento, el test
 * dice qué número esperaba y no sólo que algo falló.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "techmind.oci.enabled=false")
class MetricaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContenidoAnalizadoRepository repository;

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    private void contenido(String titulo, String categoria, double probabilidad, List<String> claves) {
        repository.save(ContenidoAnalizado.builder()
                .titulo(titulo)
                .texto("Texto de prueba con más de veinte caracteres válidos.")
                .resumen("Resumen de prueba.")
                .categoria(categoria)
                .probabilidad(probabilidad)
                .palabrasClave(claves)
                .build());
    }

    @Test
    void deberiaDevolverTableroEnCerosConLaBaseVacia() throws Exception {
        // Un repositorio sin contenidos es un estado válido, no un 404.
        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContenidos").value(0))
                .andExpect(jsonPath("$.totalCategorias").value(0))
                .andExpect(jsonPath("$.confianzaMedia").value(0.0))
                .andExpect(jsonPath("$.totalRelaciones").value(0))
                .andExpect(jsonPath("$.palabrasClaveUnicas").value(0))
                .andExpect(jsonPath("$.confianzaPorCategoria").isEmpty())
                // Los tramos se devuelven siempre, para que el histograma no
                // tenga que inventar su propia estructura cuando no hay datos.
                .andExpect(jsonPath("$.distribucionConfianza.length()").value(4));
    }

    @Test
    void deberiaContarContenidosYCategorias() throws Exception {
        contenido("A", "backend", 0.90, List.of("java"));
        contenido("B", "backend", 0.80, List.of("java"));
        contenido("C", "devops", 0.70, List.of("docker"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalContenidos").value(3))
                .andExpect(jsonPath("$.totalCategorias").value(2));
    }

    @Test
    void deberiaCalcularLaConfianzaMediaGlobal() throws Exception {
        contenido("A", "backend", 0.90, List.of("java"));
        contenido("B", "backend", 0.70, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confianzaMedia").value(0.8));
    }

    @Test
    void deberiaOrdenarLaConfianzaPorCategoriaDeMenorAMayor() throws Exception {
        contenido("A", "backend", 0.95, List.of("java"));
        contenido("B", "devops", 0.40, List.of("docker"));

        // La categoría donde el modelo duda va primero: es la que hay que mirar.
        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.confianzaPorCategoria[0].categoria").value("devops"))
                .andExpect(jsonPath("$.confianzaPorCategoria[0].confianzaMedia").value(0.4))
                .andExpect(jsonPath("$.confianzaPorCategoria[1].categoria").value("backend"));
    }

    @Test
    void deberiaUbicarCadaContenidoEnSuTramoDeConfianza() throws Exception {
        contenido("Muy baja", "backend", 0.30, List.of("java"));
        contenido("Media", "backend", 0.60, List.of("java"));
        contenido("Alta", "backend", 0.80, List.of("java"));
        contenido("Muy alta", "backend", 0.95, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribucionConfianza[0].cantidad").value(1))
                .andExpect(jsonPath("$.distribucionConfianza[1].cantidad").value(1))
                .andExpect(jsonPath("$.distribucionConfianza[2].cantidad").value(1))
                .andExpect(jsonPath("$.distribucionConfianza[3].cantidad").value(1));
    }

    @Test
    void deberiaContarUnaProbabilidadDeUnoEnElUltimoTramo() throws Exception {
        // El límite superior es exclusivo: sin el margen del tramo final, un
        // 1.0 exacto no se contaría en ningún lado.
        contenido("Certeza total", "backend", 1.0, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.distribucionConfianza[3].cantidad").value(1));
    }

    @Test
    void deberiaAgruparPalabrasClaveSinDistinguirMayusculas() throws Exception {
        contenido("A", "backend", 0.9, List.of("Java"));
        contenido("B", "backend", 0.9, List.of("java"));
        contenido("C", "backend", 0.9, List.of("JAVA"));

        // Tres escrituras del mismo término son un solo vocablo.
        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palabrasClaveUnicas").value(1))
                .andExpect(jsonPath("$.palabrasClaveTop[0].palabraClave").value("java"))
                .andExpect(jsonPath("$.palabrasClaveTop[0].cantidad").value(3));
    }

    @Test
    void deberiaOrdenarElTopDePalabrasPorFrecuencia() throws Exception {
        contenido("A", "backend", 0.9, List.of("java", "spring"));
        contenido("B", "backend", 0.9, List.of("java", "jpa"));
        contenido("C", "backend", 0.9, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palabrasClaveTop[0].palabraClave").value("java"))
                .andExpect(jsonPath("$.palabrasClaveTop[0].cantidad").value(3))
                .andExpect(jsonPath("$.palabrasClaveUnicas").value(3));
    }

    @Test
    void deberiaContarRelacionesSoloDentroDeLaMismaCategoria() throws Exception {
        // A y B comparten "java" y categoría: son un par.
        contenido("A", "backend", 0.9, List.of("java", "spring"));
        contenido("B", "backend", 0.9, List.of("java", "jpa"));
        // C comparte "java" pero es de otra categoría: no cuenta.
        contenido("C", "devops", 0.9, List.of("java", "docker"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelaciones").value(1));
    }

    @Test
    void noDeberiaContarComoRelacionAContenidosSinPalabrasEnComun() throws Exception {
        contenido("A", "backend", 0.9, List.of("java"));
        contenido("B", "backend", 0.9, List.of("python"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelaciones").value(0));
    }

    @Test
    void deberiaContarCadaParDeRelacionUnaSolaVez() throws Exception {
        // Tres contenidos que comparten todos con todos son 3 pares, no 6:
        // la relación es simétrica.
        contenido("A", "backend", 0.9, List.of("java"));
        contenido("B", "backend", 0.9, List.of("java"));
        contenido("C", "backend", 0.9, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRelaciones").value(3));
    }

    @Test
    void deberiaCalcularElPromedioDePalabrasPorContenido() throws Exception {
        contenido("A", "backend", 0.9, List.of("java", "spring", "jpa"));
        contenido("B", "backend", 0.9, List.of("java"));

        // 4 palabras asignadas sobre 2 contenidos.
        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.palabrasClavePorContenido").value(2.0));
    }

    @Test
    void deberiaAgruparLosContenidosPorDiaDeProcesamiento() throws Exception {
        contenido("A", "backend", 0.9, List.of("java"));
        contenido("B", "backend", 0.9, List.of("java"));

        // Ambos se crean en el mismo instante, así que caen en un único día.
        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.procesadosPorDia.length()").value(1))
                .andExpect(jsonPath("$.procesadosPorDia[0].cantidad").value(2));
    }

    @Test
    void deberiaInformarCuandoFueCalculadoElTablero() throws Exception {
        contenido("A", "backend", 0.9, List.of("java"));

        mockMvc.perform(get("/api/v1/metricas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calculadoEn").exists());
    }
}
