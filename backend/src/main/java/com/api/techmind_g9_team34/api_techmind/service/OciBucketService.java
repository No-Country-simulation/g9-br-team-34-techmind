package com.api.techmind_g9_team34.api_techmind.service;

import com.api.techmind_g9_team34.api_techmind.config.OciStorageConfig;
import com.oracle.bmc.model.BmcException;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import com.oracle.bmc.objectstorage.requests.GetObjectRequest;
import com.oracle.bmc.objectstorage.requests.PutObjectRequest;
import com.oracle.bmc.objectstorage.responses.GetObjectResponse;
import com.oracle.bmc.objectstorage.responses.PutObjectResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Servicio de prueba de integración contra OCI Object Storage.
 * Valida credenciales y permisos subiendo y descargando un archivo de prueba,
 * antes de que otros componentes dependan de esta integración.
 */
@Service
public class OciBucketService {

    private static final Logger log = LoggerFactory.getLogger(OciBucketService.class);

    private final ObjectStorageClient client;
    private final OciStorageConfig config;

    public OciBucketService(ObjectStorageClient client, OciStorageConfig config) {
        this.client = client;
        this.config = config;
    }

    /**
     * Sube un archivo de texto simple al bucket configurado.
     */
    public void subirArchivoDePrueba(String objectName, String contenido) {
        try {
            byte[] bytes = contenido.getBytes(StandardCharsets.UTF_8);

            PutObjectRequest request = PutObjectRequest.builder()
                    .namespaceName(config.getNamespace())
                    .bucketName(config.getBucketName())
                    .objectName(objectName)
                    .contentLength((long) bytes.length)
                    .putObjectBody(new ByteArrayInputStream(bytes))
                    .build();

            PutObjectResponse response = client.putObject(request);
            log.info("Subida OK. objectName={}, eTag={}", objectName, response.getETag());

        } catch (BmcException e) {
            // Documentación de errores de credenciales/permisos (criterio de aceptación)
            log.error("Error al subir archivo a OCI. Codigo HTTP: {}, Mensaje: {}, OpcRequestId: {}",
                    e.getStatusCode(), e.getMessage(), e.getOpcRequestId());
            throw e;
        }
    }

    /**
     * Descarga el archivo indicado y devuelve su contenido como String.
     * IMPORTANTE: cierra el stream explícitamente (ver warning del SDK sobre Apache Connector).
     */
    public String descargarArchivoDePrueba(String objectName) {
        GetObjectRequest request = GetObjectRequest.builder()
                .namespaceName(config.getNamespace())
                .bucketName(config.getBucketName())
                .objectName(objectName)
                .build();

        GetObjectResponse response = client.getObject(request);

        try (InputStream inputStream = response.getInputStream()) {

            String contenido = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            log.info("Descarga OK. objectName={}", objectName);
            return contenido;

        } catch (BmcException e) {
            log.error("Error al descargar archivo de OCI. Codigo HTTP: {}, Mensaje: {}, OpcRequestId: {}",
                    e.getStatusCode(), e.getMessage(), e.getOpcRequestId());
            throw e;
        } catch (Exception e) {
            log.error("Error inesperado al leer el stream de descarga: {}", e.getMessage());
            throw new RuntimeException("Error al descargar archivo de OCI", e);
        }
    }
}
