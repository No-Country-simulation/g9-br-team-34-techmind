package com.api.techmind_g9_team34.api_techmind.repository;

import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * TM-026 — Acceso a datos de {@link ContenidoAnalizado}.
 *
 * <p>Es una {@code interface} y no una clase: Spring Data genera la
 * implementación en tiempo de ejecución a partir de la firma de los métodos.
 * No debe escribirse una clase que la implemente.
 *
 * <p>Al extender {@link JpaRepository} ya quedan disponibles {@code save},
 * {@code findById}, {@code findAll}, {@code deleteById}, paginación y
 * ordenamiento, sin declarar nada. Los dos métodos de abajo no agregan
 * funcionalidad nueva: redeclaran heredados únicamente para adjuntarles un
 * {@code @EntityGraph}.
 *
 * <p><b>Por qué hacen falta esos overrides:</b> {@code palabrasClave} es una
 * {@code @ElementCollection}, que Hibernate carga de forma perezosa. Sin el
 * grafo, la colección se intenta leer cuando la transacción ya cerró — al
 * armar el DTO de respuesta — y lanza {@code LazyInitializationException}.
 * Con {@code @EntityGraph} las palabras clave viajan en la misma consulta,
 * lo que además evita el problema N+1 al listar.
 *
 * <p>{@code @Repository} es opcional aquí (Spring Data ya registra el bean),
 * pero se deja explícito para que la traducción de excepciones de persistencia
 * quede documentada y visible.
 */
@Repository
public interface ContenidoAnalizadoRepository extends
        JpaRepository<ContenidoAnalizado, UUID>,
        JpaSpecificationExecutor<ContenidoAnalizado> {

    /**
     * Busca un contenido por id, trayendo sus palabras clave en la misma consulta.
     *
     * <p>Consumidor previsto: TM-036 ({@code GET /api/v1/contenidos/{id}}).
     *
     * @param id identificador del contenido
     * @return el contenido con sus palabras clave ya cargadas, o vacío si no existe
     */
    @Override
    @EntityGraph(attributePaths = "palabrasClave")
    Optional<ContenidoAnalizado> findById(UUID id);

    /**
     * Lista todos los contenidos con sus palabras clave ya cargadas.
     *
     * <p>Consumidor previsto: TM-037 ({@code GET /api/v1/contenidos}).
     *
     * <p>Sin paginación: la variante paginada la introduce TM-062 cuando se
     * implemente ese endpoint, para no adelantar decisiones de ese ticket.
     *
     * @return todos los contenidos analizados
     */
    @Override
    @EntityGraph(attributePaths = "palabrasClave")
    List<ContenidoAnalizado> findAll();

    /**
     * Lista los contenidos de una categoría, con sus palabras clave ya cargadas.
     *
     * <p>Consumidor previsto: TM-037, que expone el filtro opcional
     * {@code GET /api/v1/contenidos?categoria=...}.
     *
     * <p>Spring Data deriva la consulta del nombre del método: {@code findBy} +
     * el nombre del atributo de la entidad. No hace falta escribir SQL ni JPQL.
     * Por eso el parámetro debe llamarse igual que el campo {@code categoria};
     * si el campo se renombra, este método deja de resolver y falla al arrancar
     * el contexto, no en tiempo de ejecución.
     *
     * <p>La comparación es sensible a mayúsculas y minúsculas, y espera uno de
     * los valores acordados con Ciencia de Datos en TM-006. Una categoría
     * inexistente devuelve lista vacía, no error: quien exponga el endpoint
     * decide si eso es un 200 con lista vacía o un 404.
     *
     * @param categoria categoría exacta a filtrar
     * @return contenidos de esa categoría; lista vacía si no hay ninguno
     */
    @EntityGraph(attributePaths = "palabrasClave")
    List<ContenidoAnalizado> findByCategoria(String categoria);
}
