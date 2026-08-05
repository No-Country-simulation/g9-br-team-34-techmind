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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TM-038 — Integración de {@code GET /api/v1/categorias}.
 *
 * <p>En TM-038 la {@code cantidadProcesados} se omite del JSON (es {@code null}
 * y se usa {@code @JsonInclude(NON_NULL)}); el conteo real se puebla en TM-067
 * como superset estricto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "techmind.oci.enabled=false")
class CategoriaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ContenidoAnalizadoRepository repository;

    @BeforeEach
    void limpiarBase() {
        repository.deleteAll();
    }

    private void contenido(String titulo, String categoria) {
        repository.save(ContenidoAnalizado.builder()
                .titulo(titulo)
                .texto("Texto de prueba con más de veinte caracteres válidos.")
                .categoria(categoria)
                .probabilidad(0.9)
                .palabrasClave(List.of("Java"))
                .build());
    }

    @Test
    void deberiaListarCategoriasDistintasSinCantidad() throws Exception {
        contenido("A", "Backend");
        contenido("B", "Backend");
        contenido("C", "DevOps");

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[?(@.categoria=='Backend')]").exists())
                .andExpect(jsonPath("$[?(@.categoria=='DevOps')]").exists());
    }

    @Test
    void deberiaDevolverListaVaciaCuandoNoHayCategorias() throws Exception {
        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void deberiaListarCategoriasConCantidadProcesados() throws Exception {
        contenido("A", "Backend");
        contenido("B", "Backend");
        contenido("C", "Backend");
        contenido("D", "DevOps");
        contenido("E", "DevOps");

        mockMvc.perform(get("/api/v1/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].categoria").value("Backend"))
                .andExpect(jsonPath("$[0].cantidadProcesados").value(3))
                .andExpect(jsonPath("$[1].categoria").value("DevOps"))
                .andExpect(jsonPath("$[1].cantidadProcesados").value(2));
    }

    @Test
    void deberiaMantenerAisladoElEndpointDeCategorias() throws Exception {
        String baseContenidos = RequestMapping.class
                .cast(ContenidoController.class.getAnnotation(RequestMapping.class)).value()[0];

        assertThat(baseContenidos).isEqualTo("/api/v1/contenidos");
        for (Method metodo : ContenidoController.class.getDeclaredMethods()) {
            if (metodo.isAnnotationPresent(GetMapping.class)) {
                String ruta = GetMapping.class.cast(metodo.getAnnotation(GetMapping.class)).value().length > 0
                        ? GetMapping.class.cast(metodo.getAnnotation(GetMapping.class)).value()[0]
                        : "";
                assertThat(baseContenidos + ruta).isNotEqualTo("/api/v1/categorias");
            }
        }

        String baseCategorias = RequestMapping.class
                .cast(CategoriaController.class.getAnnotation(RequestMapping.class)).value()[0];
        assertThat(baseCategorias).isEqualTo("/api/v1/categorias");

        long gettersDeCategorias = Arrays.stream(CategoriaController.class.getDeclaredMethods())
                .filter(m -> m.isAnnotationPresent(GetMapping.class))
                .count();
        assertThat(gettersDeCategorias).isEqualTo(1);
    }
}