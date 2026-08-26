package com.api.techmind_g9_team34.api_techmind.dto.response;

/**
 * Resultado del procesamiento de un archivo individual dentro de un lote
 * de PDFs (ver {@code POST /api/v1/contenidos/lote-pdf}).
 *
 * <p>Análogo a {@link FilaResultadoDTO} (usado en el lote de CSV), pero
 * identificando cada resultado por nombre de archivo en vez de número
 * de fila, ya que no hay un orden posicional significativo en un lote
 * de archivos subidos.
 */
public class ArchivoResultadoDTO {

    private String nombreArchivo;
    private String estado;
    private ContenidoResponseDTO resultado;
    private String mensajeError;

    public ArchivoResultadoDTO(
            String nombreArchivo,
            String estado,
            ContenidoResponseDTO resultado,
            String mensajeError) {

        this.nombreArchivo = nombreArchivo;
        this.estado = estado;
        this.resultado = resultado;
        this.mensajeError = mensajeError;
    }

    public String getNombreArchivo() {
        return nombreArchivo;
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
