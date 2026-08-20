package com.api.techmind_g9_team34.api_techmind.service.impl;

import com.api.techmind_g9_team34.api_techmind.dto.response.MetricasDTO;
import com.api.techmind_g9_team34.api_techmind.model.ContenidoAnalizado;
import com.api.techmind_g9_team34.api_techmind.repository.ContenidoAnalizadoRepository;
import com.api.techmind_g9_team34.api_techmind.service.MetricaService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * S5-14 — Cálculo del tablero de métricas.
 *
 * <p>Casi todo se resuelve en base con agregaciones; sólo el agrupamiento por
 * día se hace en memoria, por la razón explicada en {@link #procesadosPorDia}.
 */
@Service
public class MetricaServiceImpl implements MetricaService {

    private static final Logger logger = LoggerFactory.getLogger(MetricaServiceImpl.class);

    /** Cuántas palabras clave devuelve el ranking (M7). */
    private static final int TOP_PALABRAS = 12;

    /**
     * Tramos del histograma de confianza (M5).
     *
     * <p>Los cortes no son regulares a propósito: por debajo de 0,50 la
     * predicción es prácticamente un empate y merece su propio tramo, mientras
     * que arriba de 0,85 el modelo está cómodo. Repartir en cuartos parejos
     * mezclaría casos que conviene mirar por separado.
     *
     * <p>El último tramo llega hasta 1.01 porque el límite superior es
     * exclusivo y una probabilidad de exactamente 1.0 debe contarse.
     */
    private static final double[][] TRAMOS = {
            {0.00, 0.50}, {0.50, 0.70}, {0.70, 0.85}, {0.85, 1.01}
    };
    private static final String[] ETIQUETAS_TRAMOS = {
            "Menos de 50%", "50% a 70%", "70% a 85%", "85% o más"
    };

    private final ContenidoAnalizadoRepository repositorio;

    public MetricaServiceImpl(ContenidoAnalizadoRepository repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    @Transactional(readOnly = true)
    public MetricasDTO obtenerMetricas() {
        long total = repositorio.count();
        logger.info("Calculando tablero de métricas sobre {} contenidos", total);

        if (total == 0) {
            // Sin datos no hay nada que promediar y las agregaciones devolverían
            // nulos. Se corta acá y se responde un tablero en ceros, que el
            // dashboard sabe representar como "todavía no hay nada".
            return new MetricasDTO(0, 0, 0, 0, 0, 0, 0,
                    List.of(), tramosVacios(), List.of(), List.of(), Instant.now());
        }

        double confianza = cero(repositorio.confianzaMediaGlobal());
        double longitud = cero(repositorio.longitudMediaTexto());
        long totalPalabras = repositorio.totalPalabrasClave();

        List<MetricasDTO.ConfianzaPorCategoria> porCategoria =
                repositorio.confianzaPorCategoria().stream()
                        .map(p -> new MetricasDTO.ConfianzaPorCategoria(
                                p.getCategoria(),
                                redondear(cero(p.getConfianzaMedia())),
                                p.getCantidad()))
                        .toList();

        List<MetricasDTO.PalabraClaveFrecuente> topPalabras =
                repositorio.palabrasClaveMasFrecuentes(PageRequest.of(0, TOP_PALABRAS))
                        .stream()
                        .map(p -> new MetricasDTO.PalabraClaveFrecuente(
                                p.getPalabraClave(), p.getCantidad()))
                        .toList();

        return new MetricasDTO(
                total,
                repositorio.findCategoriasDistintas().size(),
                redondear(confianza),
                Math.round(longitud),
                redondear((double) totalPalabras / total),
                repositorio.palabrasClaveUnicas(),
                contarRelaciones(),
                porCategoria,
                distribucionConfianza(),
                procesadosPorDia(),
                topPalabras,
                Instant.now());
    }

    /** M5 — Histograma de confianza, una consulta de conteo por tramo. */
    private List<MetricasDTO.TramoConfianza> distribucionConfianza() {
        List<MetricasDTO.TramoConfianza> salida = new ArrayList<>(TRAMOS.length);
        for (int i = 0; i < TRAMOS.length; i++) {
            long cantidad = repositorio.contarPorTramoDeConfianza(TRAMOS[i][0], TRAMOS[i][1]);
            salida.add(new MetricasDTO.TramoConfianza(
                    ETIQUETAS_TRAMOS[i], TRAMOS[i][0], TRAMOS[i][1], cantidad));
        }
        return salida;
    }

    /** Tramos en cero, para responder coherente cuando la base está vacía. */
    private List<MetricasDTO.TramoConfianza> tramosVacios() {
        List<MetricasDTO.TramoConfianza> salida = new ArrayList<>(TRAMOS.length);
        for (int i = 0; i < TRAMOS.length; i++) {
            salida.add(new MetricasDTO.TramoConfianza(
                    ETIQUETAS_TRAMOS[i], TRAMOS[i][0], TRAMOS[i][1], 0));
        }
        return salida;
    }

    /**
     * M6 — Contenidos por día.
     *
     * <p>El agrupamiento se hace en Java y no en SQL porque truncar un
     * {@code Instant} a día exige funciones propias de cada motor, y esa
     * consulta se rompería al migrar de H2 a Postgres (S5-18). Se traen las
     * fechas y se cuentan acá: son pocas filas y el código queda independiente
     * de la base.
     *
     * <p>Se agrupa en UTC, que es como se persiste, para que el corte de día no
     * dependa de la zona horaria del servidor.
     */
    private List<MetricasDTO.ProcesadosPorDia> procesadosPorDia() {
        Map<LocalDate, Long> porDia = new LinkedHashMap<>();
        for (Instant fecha : repositorio.fechasDeProcesamiento()) {
            if (fecha == null) {
                continue;
            }
            LocalDate dia = fecha.atZone(ZoneOffset.UTC).toLocalDate();
            porDia.merge(dia, 1L, Long::sum);
        }
        return porDia.entrySet().stream()
                .map(e -> new MetricasDTO.ProcesadosPorDia(e.getKey().toString(), e.getValue()))
                .toList();
    }

    /**
     * M11 — Cuenta los pares de contenidos relacionados entre sí.
     *
     * <p>Aplica el mismo criterio que {@code findIdsRelacionados}: misma
     * categoría y al menos una palabra clave compartida, comparada en
     * minúsculas. Cada par se cuenta una sola vez.
     *
     * <p>Se resuelve en Java sobre una única lectura y no con SQL. Expresarlo
     * en JPQL exigiría cruzar la entidad consigo misma y además con la
     * colección de palabras clave, una consulta cara y difícil de leer; hacerlo
     * llamando N veces a {@code findIdsRelacionados} sería peor. Con el volumen
     * de contenidos del repositorio, comparar en memoria alcanza.
     *
     * <p>Nota: el costo crece con el cuadrado de la cantidad de contenidos
     * dentro de cada categoría. Si el repositorio creciera mucho, conviene
     * revisar este cálculo antes que el resto del tablero.
     */
    private long contarRelaciones() {
        // Se agrupa por categoría primero: dos contenidos de categorías
        // distintas nunca se relacionan, así que no hace falta compararlos.
        Map<String, List<Set<String>>> porCategoria = new LinkedHashMap<>();
        for (ContenidoAnalizado c : repositorio.findAllConPalabrasClave()) {
            if (c.getPalabrasClave() == null || c.getPalabrasClave().isEmpty()) {
                continue;
            }
            Set<String> claves = c.getPalabrasClave().stream()
                    .filter(Objects::nonNull)
                    .map(k -> k.toLowerCase(Locale.ROOT))
                    .collect(Collectors.toSet());
            if (claves.isEmpty()) {
                continue;
            }
            porCategoria.computeIfAbsent(c.getCategoria(), k -> new ArrayList<>()).add(claves);
        }

        long pares = 0;
        for (List<Set<String>> grupo : porCategoria.values()) {
            for (int i = 0; i < grupo.size(); i++) {
                for (int j = i + 1; j < grupo.size(); j++) {
                    if (!Collections.disjoint(grupo.get(i), grupo.get(j))) {
                        pares++;
                    }
                }
            }
        }
        return pares;
    }

    /** {@code avg} sobre cero filas devuelve nulo en SQL; acá se vuelve cero. */
    private double cero(Double valor) {
        return valor == null ? 0d : valor;
    }

    /** Dos decimales: más precisión no aporta nada a quien lee el tablero. */
    private double redondear(double valor) {
        return Math.round(valor * 100d) / 100d;
    }
}
