package com.api.techmind_g9_team34.api_techmind.config;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test de slicing para {@link WebClientConfig}.
 * <p>
 * Registra unicamente {@link WebClientConfig} en un {@link AnnotationConfigApplicationContext}
 * con las propiedades que necesita, evitando arrancar el contexto completo de Spring Boot
 * (y por tanto sin requerir la configuracion de OCI).
 */
class WebClientConfigTest {

    @Test
    void deberiaCrearBeanRestClient() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new MapPropertySource(
                            "test-props",
                            Map.of("techmind.inference-service.timeout-ms", 5000)
                    ));
            context.register(WebClientConfig.class);
            context.refresh();

            RestClient inferenceRestClient = context.getBean("inferenceRestClient", RestClient.class);

            assertThat(inferenceRestClient).isNotNull();
        }
    }
}
