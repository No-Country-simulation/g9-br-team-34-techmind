package com.api.techmind_g9_team34.api_techmind.service.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Extrae título y texto de archivos Word (.docx) usando Apache POI.
 */
@Service
public class DocxParserService {

    public record ResultadoExtraccion(String titulo, String texto) {
    }

    public ResultadoExtraccion extraer(byte[] contenido) throws IOException {
        try (XWPFDocument documento = new XWPFDocument(new ByteArrayInputStream(contenido))) {

            List<XWPFParagraph> parrafos = documento.getParagraphs().stream()
                    .filter(p -> p.getText() != null && !p.getText().isBlank())
                    .toList();

            if (parrafos.isEmpty()) {
                return new ResultadoExtraccion("Sin título", "");
            }

            XWPFParagraph primerParrafo = parrafos.get(0);
            String estilo = primerParrafo.getStyle();
            boolean pareceTitulo = estilo != null
                    && (estilo.toLowerCase().contains("title") || estilo.toLowerCase().contains("heading"));

            String titulo = primerParrafo.getText().strip();
            String texto = pareceTitulo
                    ? parrafos.stream().skip(1).map(XWPFParagraph::getText).collect(Collectors.joining("\n"))
                    : parrafos.stream().map(XWPFParagraph::getText).collect(Collectors.joining("\n"));

            return new ResultadoExtraccion(titulo, texto);
        }
    }
}
