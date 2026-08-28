package com.tailorkz.gestao_entidades.domain.service;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.stereotype.Service;

import java.io.File;

@Service
public class PdfCompressorService {

    public void comprimirPdf(String caminhoArquivoFisico) {
        if (!caminhoArquivoFisico.toLowerCase().endsWith(".pdf")) {
            return; // Se for imagem, apenas ignora
        }

        try {
            File arquivoOriginal = new File(caminhoArquivoFisico);

            // Carrega o PDF pesado
            try (PDDocument documento = PDDocument.load(arquivoOriginal)) {

                // Ao sobrescrever o arquivo original, ele elimina lixos de formatação
                documento.save(arquivoOriginal);
            }

            System.out.println("✅ PDF comprimido com sucesso: " + arquivoOriginal.getName());

        } catch (Exception e) {
            System.err.println("⚠️ Falha ao comprimir o PDF (mantendo o original): " + e.getMessage());
        }
    }
}