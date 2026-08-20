package com.api.techmind_g9_team34.api_techmind.dto.response;

import java.util.List;

/**
 * Resultado agregado de procesar un lote de PDFs
 * (ver {@code POST /api/v1/contenidos/lote-pdf}).
 *
 * <p>Análogo a {@link ContenidoLoteResultadoDTO} (lote de CSV), pero con
 * {@link ArchivoResultadoDTO} en vez de {@link FilaResultadoDTO}.
 */
public class ContenidoLotePdfResultadoDTO {

    private int totalArchivos;
    private int procesadosExitosos;
    private int procesadosConError;
    private List<ArchivoResultadoDTO> resultados;

    public ContenidoLotePdfResultadoDTO(
            int totalArchivos,
            int procesadosExitosos,
            int procesadosConError,
            List<ArchivoResultadoDTO> resultados) {

        this.totalArchivos = totalArchivos;
        this.procesadosExitosos = procesadosExitosos;
        this.procesadosConError = procesadosConError;
        this.resultados = resultados;
    }

    public int getTotalArchivos() {
        return totalArchivos;
    }

    public int getProcesadosExitosos() {
        return procesadosExitosos;
    }

    public int getProcesadosConError() {
        return procesadosConError;
    }

    public List<ArchivoResultadoDTO> getResultados() {
        return resultados;
    }
}
