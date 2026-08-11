package com.api.techmind_g9_team34.api_techmind.dto.response;

import java.util.List;

public class LoteContenidoResponseDTO {

    private List<ContenidoResponseDTO> exitos;
    private List<String> rechazados;

    public LoteContenidoResponseDTO() {
    }

    public LoteContenidoResponseDTO(
            List<ContenidoResponseDTO> exitos,
            List<String> rechazados) {
        this.exitos = exitos;
        this.rechazados = rechazados;
    }

    public List<ContenidoResponseDTO> getExitos() {
        return exitos;
    }

    public void setExitos(List<ContenidoResponseDTO> exitos) {
        this.exitos = exitos;
    }

    public List<String> getRechazados() {
        return rechazados;
    }

    public void setRechazados(List<String> rechazados) {
        this.rechazados = rechazados;
    }
}