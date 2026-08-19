package com.api.techmind_g9_team34.api_techmind.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class WebClientConfig {

    @Bean
    public RestClient inferenceRestClient(
            @Value("${techmind.inference-service.timeout-ms}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Bean
    public RestClient geminiRestClient(
            @Value("${techmind.gemini.timeout-ms:60000}") int timeoutMs) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeoutMs);
        factory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}