package com.api.techmind_g9_team34.api_techmind.repository;

import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.projection.ConteoCategoria;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
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

    /**
     * Devuelve las categorías distintas presentes en la base de datos.
     *
     * <p>Consumidor previsto: TM-038 ({@code GET /api/v1/categorias}). Sin
     * orden definido en esta versión (TM-038); el conteo agrupado lo agrega
     * TM-067.
     *
     * @return lista de categorías distintas; vacía si no hay contenidos
     */
    @Query("select distinct c.categoria from ContenidoAnalizado c")
    List<String> findCategoriasDistintas();

    /**
     * TM-049 — Busca contenidos relacionados a uno dado.
     *
     * <p>Criterio de relación: <b>misma categoría y al menos una palabra clave
     * compartida</b>, ordenando de mayor a menor cantidad de palabras clave en
     * común. Es determinístico y no depende del servicio de inferencia; la
     * especificación de la API deja el algoritmo a criterio del equipo.
     *
     * <p>Detalles de la consulta que conviene tener presentes:
     *
     * <ul>
     *   <li><b>Se excluye el propio contenido base</b> ({@code c.id <> :id}).
     *       Sin eso siempre saldría primero, porque comparte todas sus palabras
     *       clave consigo mismo.</li>
     *   <li>El {@code join} sobre {@code palabrasClave} recorre la tabla
     *       {@code contenido_palabras_clave}; cada coincidencia produce una fila,
     *       y el {@code group by} las colapsa en un contenido por grupo.</li>
     *   <li>El desempate por {@code fechaProcesamiento} descendente evita que
     *       dos contenidos con la misma cantidad de coincidencias salgan en
     *       orden aleatorio entre ejecuciones, lo que haría los tests
     *       intermitentes.</li>
     *   <li>Las palabras clave se comparan en minúsculas: el modelo puede
     *       devolver "Java" y "java" como formas distintas de lo mismo.</li>
     * </ul>
     *
     * <p>Devuelve identificadores y no entidades porque el orden lo impone el
     * {@code group by}, y volver a traer las entidades con {@code findAllById}
     * lo perdería. El servicio reordena según esta lista.
     *
     * @param id         contenido base
     * @param categoria  categoría del contenido base
     * @param pageable   límite de resultados (TM-068)
     * @return ids de los contenidos relacionados, del más al menos similar
     */
    @Query("""
            select c.id
            from ContenidoAnalizado c
            join c.palabrasClave p
            where c.id <> :id
              and c.categoria = :categoria
              and lower(p) in :palabrasClave
            group by c.id, c.fechaProcesamiento
            order by count(p) desc, c.fechaProcesamiento desc
            """)
    List<UUID> findIdsRelacionados(
            @Param("id") UUID id,
            @Param("categoria") String categoria,
            @Param("palabrasClave") Collection<String> palabrasClave,
            Pageable pageable);

    /**
     * Cuenta los contenidos agrupados por categoría.
     *
     * <p>Consumidor previsto: TM-067 ({@code GET /api/v1/categorias}). Los
     * alias del {@code select} ({@code categoria}, {@code cantidadProcesados})
     * deben coincidir con los getters de {@link ConteoCategoria}. Se ordena por
     * categoría para un resultado determinístico.
     *
     * @return conteo por categoría ordenado alfabéticamente
     */
    @Query("select c.categoria as categoria, count(c) as cantidadProcesados " +
            "from ContenidoAnalizado c group by c.categoria order by c.categoria")
    List<ConteoCategoria> contarPorCategoria();
}
