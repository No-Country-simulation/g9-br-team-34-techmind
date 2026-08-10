package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.dto.response.OciPruebaResultadoDTO;
import com.api.techmind_g9_team34.api_techmind.service.OciBucketService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint temporal de prueba de integración con OCI Object Storage (TM-014).
 *
 * <p>Delega toda la lógica (subir, descargar y comparar) en
 * {@link OciBucketService}; el controlador solo decide el código HTTP según el
 * resultado de la prueba (TM-057: usar exclusivamente DTOs y no contener lógica
 * de negocio).
 */
@RestController
@ConditionalOnProperty(name = "techmind.oci.enabled", matchIfMissing = true)
public class OciTestController {

    private final OciBucketService ociBucketService;

    public OciTestController(OciBucketService ociBucketService) {
        this.ociBucketService = ociBucketService;
    }

    @PostMapping("/api/test/oci")
    public ResponseEntity<OciPruebaResultadoDTO> probarIntegracionOci() {
        OciPruebaResultadoDTO resultado = ociBucketService.probarIntegracion();
        HttpStatus status = resultado.error()
                ? HttpStatus.INTERNAL_SERVER_ERROR
                : HttpStatus.OK;
        return ResponseEntity.status(status).body(resultado);
    }
}