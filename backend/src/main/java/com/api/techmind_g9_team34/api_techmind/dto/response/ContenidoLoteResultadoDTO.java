package com.api.techmind_g9_team34.api_techmind.dto.response;

import java.util.List;

public class ContenidoLoteResultadoDTO {

    private int totalFilas;
    private int procesadosExitosos;
    private int procesadosConError;
    private List<FilaResultadoDTO> resultados;

    public ContenidoLoteResultadoDTO(
            int totalFilas,
            int procesadosExitosos,
            int procesadosConError,
            List<FilaResultadoDTO> resultados) {

        this.totalFilas = totalFilas;
        this.procesadosExitosos = procesadosExitosos;
        this.procesadosConError = procesadosConError;
        this.resultados = resultados;
    }

    public int getTotalFilas() {
        return totalFilas;
    }

    public int getProcesadosExitosos() {
        return procesadosExitosos;
    }

    public int getProcesadosConError() {
        return procesadosConError;
    }

    public List<FilaResultadoDTO> getResultados() {
        return resultados;
    }
}