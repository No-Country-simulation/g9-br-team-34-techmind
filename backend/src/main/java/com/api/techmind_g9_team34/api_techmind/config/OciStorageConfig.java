package com.api.techmind_g9_team34.api_techmind.config;

import com.oracle.bmc.auth.AuthenticationDetailsProvider;
import com.oracle.bmc.auth.ConfigFileAuthenticationDetailsProvider;
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider;
import com.oracle.bmc.objectstorage.ObjectStorageClient;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Base64;

/**
 * Configuración para Oracle Cloud Infrastructure (OCI) Object Storage.
 *
 * Carga automáticamente las propiedades definidas con el prefijo
 * "techmind.oci" utilizando @ConfigurationProperties.
 */
@Configuration
@ConfigurationProperties(prefix = "techmind.oci")
@ConditionalOnProperty(name = "techmind.oci.enabled", matchIfMissing = true)
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
     * Se admite UNA de las dos formas de proveer la clave privada:
     * - privateKeyContent: clave .pem codificada en Base64 (recomendado para Docker)
     * - privateKeyPath: ruta a un archivo .pem montado (alternativa, ej. Docker Secrets)
     */
    private String userOcid;
    private String tenancyOcid;
    private String fingerprint;
    private String privateKeyContent;
    private String privateKeyPath;
    private String privateKeyPassphrase;

    /**
     * Crea el proveedor de autenticación según authMethod.
     * Soporta "config_file" (desarrollo local) y "env_vars" (producción/CI/Docker).
     */
    @Bean
    public AuthenticationDetailsProvider ociAuthenticationDetailsProvider() throws IOException {
        if ("env_vars".equalsIgnoreCase(authMethod)) {
            byte[] keyBytes = resolvePrivateKeyBytes();

            return SimpleAuthenticationDetailsProvider.builder()
                    .userId(userOcid)
                    .tenantId(tenancyOcid)
                    .fingerprint(fingerprint)
                    .privateKeySupplier(() -> new ByteArrayInputStream(keyBytes))
                    .passPhrase(privateKeyPassphrase)
                    .region(com.oracle.bmc.Region.fromRegionCodeOrId(region))
                    .build();
        }

        // Default: config_file
        return new ConfigFileAuthenticationDetailsProvider(
                resolvePath(configFile),
                configProfile
        );
    }

    /**
     * Cliente de Object Storage listo para usar en el resto del backend
     * (por ejemplo, en OciBucketService).
     */
    @Bean
    public ObjectStorageClient objectStorageClient(AuthenticationDetailsProvider provider) {
        ObjectStorageClient client = ObjectStorageClient.builder().build(provider);
        if (region != null && !region.isBlank()) {
            client.setRegion(region);
        }
        return client;
    }

    /**
     * Resuelve los bytes de la clave privada en modo env_vars.
     * Prioriza privateKeyContent (Base64); si no está presente, cae a privateKeyPath (archivo).
     */
    private byte[] resolvePrivateKeyBytes() throws IOException {
        if (privateKeyContent != null && !privateKeyContent.isBlank()) {
            return Base64.getDecoder().decode(privateKeyContent);
        }

        if (privateKeyPath != null && !privateKeyPath.isBlank()) {
            return Files.readAllBytes(Paths.get(resolvePath(privateKeyPath)));
        }

        throw new IllegalStateException(
            "Modo env_vars requiere OCI_PRIVATE_KEY_CONTENT (Base64) u OCI_PRIVATE_KEY_PATH (archivo)."
        );
    }

    /**
     * Expande el "~" en rutas de archivo (Files.readAllBytes no lo resuelve solo).
     */
    private String resolvePath(String path) {
        if (path != null && path.startsWith("~")) {
            return path.replaceFirst("^~", System.getProperty("user.home"));
        }
        return path;
    }
}