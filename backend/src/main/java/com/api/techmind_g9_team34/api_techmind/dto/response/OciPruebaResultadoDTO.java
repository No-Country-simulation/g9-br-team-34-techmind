package com.api.techmind_g9_team34.api_techmind.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * TM-057 — Resultado de la prueba de integración con OCI Object Storage.
 *
 * <p>DTO de respuesta del endpoint temporal /api/test/oci (TM-014). Reemplaza
 * el {@code Map} que el controlador construía a mano: permite que el
 * {@link com.api.techmind_g9_team34.api_techmind.controller.OciTestController}
 * devuelva únicamente DTOs y que la lógica de la prueba viva en la capa de
 * servicio. Conserva las mismas claves que la respuesta original para no
 * cambiar el contrato del endpoint.
 *
 * <p>Los campos de error ({@code codigoHttp}, {@code mensaje},
 * {@code opcRequestId}) solo se completan cuando {@code error} es {@code true};
 * en un resultado exitoso quedan {@code null} y Jackson los omite del JSON.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record OciPruebaResultadoDTO(

        String objectName,
        boolean subida,
        boolean descarga,
        boolean contenidoCoincide,
        boolean error,
        Integer codigoHttp,
        String mensaje,
        String opcRequestId

) {
}