package com.api.techmind_g9_team34.api_techmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OciStorageConfig {

    @Value("${techmind.oci.auth-method}")
    private String authMethod;

    @Value("${techmind.oci.namespace}")
    private String namespace;

    @Value("${techmind.oci.bucket-name}")
    private String bucketName;

    @Value("${techmind.oci.region}")
    private String region;

    @Value("${techmind.oci.config-file}")
    private String configFile;

    @Value("${techmind.oci.config-profile}")
    private String configProfile;

    @Value("${techmind.oci.user-ocid}")
    private String userOcid;

    @Value("${techmind.oci.tenancy-ocid}")
    private String tenancyOcid;

    @Value("${techmind.oci.fingerprint}")
    private String fingerprint;

    @Value("${techmind.oci.private-key-path}")
    private String privateKeyPath;

    @Value("${techmind.oci.private-key-passphrase}")
    private String privateKeyPassphrase;

    /*
     * TODO:
     * Integrar el SDK oficial de Oracle Cloud Infrastructure.
     *
     * Pendiente:
     * - Crear AuthenticationDetailsProvider.
     * - Soportar auth-method=config_file.
     * - Soportar auth-method=env_vars.
     * - Crear ObjectStorageClient.
     * - Registrar el cliente como Bean de Spring.
     */
}