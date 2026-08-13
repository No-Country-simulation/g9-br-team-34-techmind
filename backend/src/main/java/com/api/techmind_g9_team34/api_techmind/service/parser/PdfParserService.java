package com.api.techmind_g9_team34.api_techmind.service.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;

import java.io.IOException;

/**
 * Extrae título y texto de archivos PDF con texto seleccionable
 * (no escaneados) usando Apache PDFBox.
 *
 * Si el PDF es una imagen escaneada, {@code PDFTextStripper} devuelve
 * texto vacío o casi vacío — es responsabilidad de
 * {@code ExtraccionArchivoServiceImpl} detectar ese caso y activar el
 * fallback de Gemini, no de este parser.
 *
 * También se mide el texto de la primera página por separado
 * del total del documento: es común que una carátula/portada sea una
 * imagen (título incrustado como diseño gráfico, no como texto
 * seleccionable) mientras el resto del documento sí tiene texto
 * normal. Si solo se mide el total, ese caso pasa desapercibido —
 * el documento "tiene texto de sobra" aunque la página 1 esté vacía.
 */
@Service
public class PdfParserService {

    public record ResultadoExtraccion(String titulo, String texto, int caracteresPrimeraPagina) {
    }

    public ResultadoExtraccion extraer(byte[] contenido) throws IOException {
        try (PDDocument documento = Loader.loadPDF(contenido)) {

            String tituloMetadata = documento.getDocumentInformation() != null
                    ? documento.getDocumentInformation().getTitle()
                    : null;

            PDFTextStripper stripper = new PDFTextStripper();
            String texto = stripper.getText(documento).strip();

            String textoPrimeraPagina = extraerTextoPrimeraPagina(documento);

            String titulo = (tituloMetadata != null && !tituloMetadata.isBlank())
                    ? tituloMetadata.strip()
                    : primeraLineaNoVacia(texto);

            return new ResultadoExtraccion(titulo, texto, textoPrimeraPagina.length());
        }
    }

    private String extraerTextoPrimeraPagina(PDDocument documento) throws IOException {
        if (documento.getNumberOfPages() == 0) {
            return "";
        }
        PDFTextStripper stripper = new PDFTextStripper();
        stripper.setStartPage(1);
        stripper.setEndPage(1);
        return stripper.getText(documento).strip();
    }

    private String primeraLineaNoVacia(String texto) {
        return texto.lines()
                .map(String::strip)
                .filter(linea -> !linea.isEmpty())
                .findFirst()
                .orElse("Sin título");
    }
}