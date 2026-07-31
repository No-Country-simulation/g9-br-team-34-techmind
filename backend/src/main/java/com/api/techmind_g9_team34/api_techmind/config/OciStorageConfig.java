package com.api.techmind_g9_team34.api_techmind.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuración para Oracle Cloud Infrastructure (OCI) Object Storage.
 *
 * Carga automáticamente las propiedades definidas con el prefijo
 * "techmind.oci" utilizando @ConfigurationProperties.
 *
 * Esta configuración será utilizada posteriormente para crear el
 * AuthenticationDetailsProvider y el ObjectStorageClient.
 */
@Configuration
@ConfigurationProperties(prefix = "techmind.oci")
@Getter
@Setter
public class OciStorageConfig {

    /**
     * Método de autenticación.
     * Valores soportados:
     * - config_file
     * - env_vars
     */
    private String authMethod;

    /** Namespace del Object Storage. */
    private String namespace;

    /** Nombre del bucket donde se almacenan los modelos. */
    private String bucketName;

    /** Región de Oracle Cloud Infrastructure. */
    private String region;

    /**
     * Configuración utilizada cuando authMethod = "config_file".
     */
    private String configFile;
    private String configProfile;

    /**
     * Configuración utilizada cuando authMethod = "env_vars".
     */
    private String userOcid;
    private String tenancyOcid;
    private String fingerprint;
    private String privateKeyPath;
    private String privateKeyPassphrase;

    /*
     * Próximos pasos:
     *
     * - Crear el AuthenticationDetailsProvider.
     * - Soportar autenticación mediante:
     *     - config_file
     *     - env_vars
     * - Crear el ObjectStorageClient.
     * - Registrar los Beans de Spring.
     * - Validar la conexión con OCI Object Storage.
     */
}