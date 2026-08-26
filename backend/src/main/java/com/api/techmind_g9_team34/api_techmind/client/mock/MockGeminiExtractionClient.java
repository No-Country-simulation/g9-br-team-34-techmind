package com.api.techmind_g9_team34.api_techmind.client.mock;

import com.api.techmind_g9_team34.api_techmind.client.GeminiExtractionClient;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("mock")
public class MockGeminiExtractionClient implements GeminiExtractionClient {

    @Override
    public String extraerDeArchivo(byte[] contenido, String mimeType) {
        return """
                {"titulo": "Documento de prueba", "texto": "Texto extraído simulado para pruebas locales."}""";
    }

    @Override
    public String limpiar(String tituloCrudo, String textoCrudo) {
        return """
                {"titulo": "%s", "texto": "%s"}""".formatted(tituloCrudo, textoCrudo);
    }

    @Override
    public String resumir(String texto) {
        return "Resumen simulado para pruebas locales.";
    }
}