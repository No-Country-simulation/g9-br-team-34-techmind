package com.api.techmind_g9_team34.api_techmind.repository;

import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.projection.ConteoCategoria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TM-037 — Verifica el repositorio paginado/filtrable con Specifications.
 *
 * <p>Cubre el {@code @DataJpaTest} de la Specification de {@code categoria}
 * (T018): el {@code JpaSpecificationExecutor} heredado permite filtrar con
 * {@code findAll(spec)}, y este test garantiza que el filtro por categoría
 * devuelve exclusivamente los contenidos de esa categoría.
 */
@DataJpaTest
@ActiveProfiles("test")
@TestPropertySource(properties = "techmind.oci.enabled=false")
class ContenidoAnalizadoRepositoryTest {

    @Autowired
    private ContenidoAnalizadoRepository repository;

    private ContenidoAnalizado contenido(String titulo, String categoria) {
        return ContenidoAnalizado.builder()
                .titulo(titulo)
                .texto("Texto de prueba con más de veinte caracteres válidos.")
                .resumen("Resumen de prueba.")
                .categoria(categoria)
                .probabilidad(0.9)
                .palabrasClave(List.of("Java"))
                .build();
    }

    @Test
    void deberiaFiltrarPorCategoriaConSpecification() {
        repository.save(contenido("A", "Backend"));
        repository.save(contenido("B", "Backend"));
        repository.save(contenido("C", "DevOps"));

        Specification<ContenidoAnalizado> spec = (root, query, cb) ->
                cb.equal(root.get("categoria"), "Backend");

        List<ContenidoAnalizado> resultado = repository.findAll(spec);

        assertThat(resultado).hasSize(2);
        assertThat(resultado).allMatch(c -> c.getCategoria().equals("Backend"));
    }

    @Test
    void deberiaDevolverListaVaciaParaCategoriaInexistente() {
        repository.save(contenido("A", "Backend"));

        Specification<ContenidoAnalizado> spec = (root, query, cb) ->
                cb.equal(root.get("categoria"), "Inexistente");

        List<ContenidoAnalizado> resultado = repository.findAll(spec);

        assertThat(resultado).isEmpty();
    }

    @Test
    void deberiaDevolverCategoriasDistintas() {
        repository.save(contenido("A", "Backend"));
        repository.save(contenido("B", "Backend"));
        repository.save(contenido("C", "DevOps"));

        List<String> categorias = repository.findCategoriasDistintas();

        assertThat(categorias).containsExactlyInAnyOrder("Backend", "DevOps");
    }

    @Test
    void deberiaDevolverListaVaciaDeCategoriasCuandoLaBaseEstaVacia() {
        List<String> categorias = repository.findCategoriasDistintas();

        assertThat(categorias).isEmpty();
    }

    @Test
    void deberiaContarContenidosPorCategoria() {
        repository.save(contenido("A", "Backend"));
        repository.save(contenido("B", "Backend"));
        repository.save(contenido("C", "Backend"));
        repository.save(contenido("D", "DevOps"));
        repository.save(contenido("E", "DevOps"));

        List<ConteoCategoria> conteos = repository.contarPorCategoria();

        assertThat(conteos)
                .anyMatch(c -> c.getCategoria().equals("Backend") && c.getCantidadProcesados() == 3)
                .anyMatch(c -> c.getCategoria().equals("DevOps") && c.getCantidadProcesados() == 2);
    }
}