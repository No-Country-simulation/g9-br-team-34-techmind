package com.api.techmind_g9_team34.api_techmind.model;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * TM-024 — Entidad de persistencia del resultado de un análisis.
 *
 * <p>Representa un contenido técnico que ya fue procesado por el servicio de
 * inferencia: guarda el texto original junto con el resumen generado,
 * la categoría predicha, su confianza y las palabras clave extraídas.
 *
 * <p><b>Sobre el identificador:</b> se usa {@link UUID} y no un autoincremental.
 * La decisión está tomada a nivel proyecto: {@code ContenidoResponseDTO} ya
 * expone el id como UUID y TM-064 valida formato UUID en el path variable.
 * {@code GenerationType.UUID} es soporte nativo de Hibernate 6 (Spring Boot 3),
 * no requiere generador propio.
 *
 * <p><b>Sobre Lombok:</b> se usan anotaciones granulares y no {@code @Data}.
 * {@code @Data} genera {@code equals}, {@code hashCode} y {@code toString}
 * sobre todos los campos, lo que en una entidad JPA dispara la carga de las
 * colecciones perezosas y produce resultados inconsistentes entre una instancia
 * nueva y una ya persistida. Por eso {@code equals}/{@code hashCode} se escriben
 * a mano más abajo.
 */
@Entity
@Table(
        name = "contenidos_analizados",
        indexes = {
                // TM-037 filtra el listado por categoría; TM-067 cuenta por categoría.
                @Index(name = "idx_contenido_categoria", columnList = "categoria")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ContenidoAnalizado {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** Título enviado por el cliente. Longitud alineada con {@code ContenidoRequestDTO}. */
    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    /**
     * Texto original analizado.
     *
     * <p>Se persiste, y no solo el resultado, porque TM-049 (contenidos
     * relacionados) necesita comparar contenidos entre sí, y porque permite
     * reprocesar si el modelo cambia sin volver a pedir el texto al cliente.
     * La longitud replica el máximo aceptado en la validación de entrada.
     */
    @Column(name = "texto", nullable = false, length = 10_000)
    private String texto;

    /**
     * Resumen generado por Gemini a partir del texto completo del contenido.
     *
     * <p>TM-05 — Se persiste para poder exponerlo posteriormente mediante
     * {@code ContenidoResponseDTO}.
     *
     * <p>La longitud máxima de 2000 caracteres es provisoria hasta confirmar
     * el contrato definitivo con Ciencia de Datos.
     */
    // TODO S5-05: confirmar longitud máxima con Ciencia de Datos (2000 es provisorio)
    @Column(name = "resumen", nullable = false, length = 2000)
    private String resumen;

    /**
     * Categoría predicha por el modelo.
     *
     * <p>Se guarda como {@code String} y no como {@code enum}: el conjunto de
     * categorías lo define Ciencia de Datos (7 al momento del acuerdo TM-006) y
     * si agregan una, un enum obligaría a recompilar el backend para no romper
     * la deserialización. El conjunto válido se documenta en el contrato.
     */
    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria;

    /** Confianza de la clasificación, en el rango [0.0, 1.0]. */
    @Column(name = "probabilidad", nullable = false)
    private Double probabilidad;

    /**
     * Palabras clave extraídas del texto (entre 1 y 20 según el acuerdo con
     * Ciencia de Datos). Corresponde al campo {@code informacion_adicional} del
     * contrato público; el nombre difiere a propósito, porque la entidad modela
     * el dominio y no el contrato externo. La traducción la hace el mapper
     * (TM-061).
     *
     * <p>Se modela con {@code @ElementCollection} y no como una única columna de
     * texto para que TM-050 (búsqueda por palabra clave) pueda consultarlas
     * directamente en SQL en lugar de recorrer cadenas concatenadas.
     *
     * <p>La carga es perezosa por defecto. Los métodos del repositorio que
     * devuelven contenidos al cliente usan {@code @EntityGraph} para traerlas
     * en la misma consulta y evitar {@code LazyInitializationException} fuera
     * de la transacción.
     */
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(
            name = "contenido_palabras_clave",
            joinColumns = @JoinColumn(name = "contenido_id")
    )
    @Column(name = "palabra_clave", nullable = false, length = 100)
    @Builder.Default
    private List<String> palabrasClave = new ArrayList<>();

    /**
     * Momento del procesamiento, en UTC.
     *
     * <p>Lo completa Hibernate al insertar; no debe setearse a mano. Es
     * {@code updatable = false} porque un análisis no se re-fecha: si se
     * reprocesa, corresponde un registro nuevo.
     */
    @CreationTimestamp
    @Column(name = "fecha_procesamiento", nullable = false, updatable = false)
    private Instant fechaProcesamiento;

    /**
     * Igualdad basada exclusivamente en el identificador.
     *
     * <p>Dos instancias sin persistir nunca son iguales, aunque tengan los
     * mismos valores: todavía no representan la misma fila. Comparar por todos
     * los campos rompería el contrato de {@code equals} en cuanto la entidad
     * se actualice.
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ContenidoAnalizado otro)) {
            return false;
        }
        return id != null && id.equals(otro.id);
    }

    /**
     * Hash constante por clase.
     *
     * <p>Es el patrón recomendado para entidades JPA: el id es nulo antes de
     * persistir y se asigna después, de modo que un hash derivado del id
     * cambiaría mientras la instancia está dentro de un {@code HashSet},
     * volviéndola irrecuperable.
     */
    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}