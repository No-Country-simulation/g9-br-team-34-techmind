package com.api.techmind_g9_team34.api_techmind.dto.response;

public class FilaResultadoDTO {

    private int fila;
    private String estado;
    private ContenidoResponseDTO resultado;
    private String mensajeError;

    public FilaResultadoDTO(
            int fila,
            String estado,
            ContenidoResponseDTO resultado,
            String mensajeError) {

        this.fila = fila;
        this.estado = estado;
        this.resultado = resultado;
        this.mensajeError = mensajeError;
    }

    public int getFila() {
        return fila;
    }

    public String getEstado() {
        return estado;
    }

    public ContenidoResponseDTO getResultado() {
        return resultado;
    }

    public String getMensajeError() {
        return mensajeError;
    }
}