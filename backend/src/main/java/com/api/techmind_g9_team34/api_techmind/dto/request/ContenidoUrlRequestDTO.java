package com.api.techmind_g9_team34.api_techmind.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

/**
 * Entrada del endpoint POST /api/v1/contenidos/url — para consultas de
 * foro u otro contenido accesible por URL, en vez de archivo subido.
 */
public record ContenidoUrlRequestDTO(

        @NotBlank(message = "El campo 'url' es obligatorio y no puede estar vacío.")
        @URL(message = "El campo 'url' debe ser una URL válida (http/https).")
        String url

) {
}
