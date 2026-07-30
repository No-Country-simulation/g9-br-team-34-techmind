package com.api.techmind_g9_team34.api_techmind.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class WebClientConfigTest {

    @Autowired(required = false)
    private RestClient inferenceRestClient;

    @Test
    void deberiaCrearBeanRestClient() {
        assertThat(inferenceRestClient).isNotNull();
    }
}