package com.api.techmind_g9_team34.api_techmind.controller;

import com.api.techmind_g9_team34.api_techmind.service.OciBucketService;
import com.oracle.bmc.model.BmcException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Endpoint temporal de prueba de integración con OCI Object Storage (TM-014).
 * Sube y descarga un archivo de prueba para validar credenciales y permisos
 * antes de que otros componentes dependan de esta integración.
 */
@RestController
public class OciTestController {

    private final OciBucketService ociBucketService;

    public OciTestController(OciBucketService ociBucketService) {
        this.ociBucketService = ociBucketService;
    }

    @PostMapping("/api/test/oci")
    public ResponseEntity<Map<String, Object>> probarIntegracionOci() {
        String objectName = "prueba-integracion.txt";
        String contenidoOriginal = "Archivo de prueba - " + System.currentTimeMillis();

        Map<String, Object> resultado = new LinkedHashMap<>();

        try {
            ociBucketService.subirArchivoDePrueba(objectName, contenidoOriginal);
            resultado.put("subida", "OK");

            String contenidoDescargado = ociBucketService.descargarArchivoDePrueba(objectName);
            resultado.put("descarga", "OK");

            boolean coincide = contenidoOriginal.equals(contenidoDescargado);
            resultado.put("contenidoCoincide", coincide);
            resultado.put("objectName", objectName);

            return ResponseEntity.ok(resultado);

        } catch (BmcException e) {
            // Documentación de errores de credenciales/permisos
            resultado.put("error", true);
            resultado.put("codigoHttp", e.getStatusCode());
            resultado.put("mensaje", e.getMessage());
            resultado.put("opcRequestId", e.getOpcRequestId());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(resultado);
        }
    }
}
