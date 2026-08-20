package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.controller.dto.DadosNotaDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    public DadosNotaDTO extrairDadosPdf(MultipartFile arquivo) {
        try (PDDocument document = PDDocument.load(arquivo.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();

            // remove as quebras
            String textoPdf = stripper.getText(document).replaceAll("\r", "");

            // PRINT PARA DEPURAÇÃO
            System.out.println("=== LEITURA PDF ===");
            System.out.println(textoPdf);
            System.out.println("===============================");

            // Condição mais permissiva
            if (textoPdf.contains("NFS-e") || textoPdf.contains("Prestador") || textoPdf.contains("DANFSe")) {
                return lerNotaServico(textoPdf);
            } else {
                return lerNotaProduto(textoPdf);
            }

        } catch (Exception e) {
            throw new RuntimeException("Falha ao processar o PDF da Nota Fiscal", e);
        }
    }

    private DadosNotaDTO lerNotaServico(String texto) {
        // 1. pega a linha inteira
        String emitenteBruto = extrairPorRegex(texto, "Nome\\s*/\\s*Nome\\s*Empresarial\\s*\\n(.*?)\\n");

        // remove pontos e traços
        String emitente = emitenteBruto.replaceAll("[0-9.-]", "").trim();

        String valor = extrairPorRegex(texto, "VALOR\\s*TOTAL.*?R\\$\\s*([\\d.,]+)");
        if (valor.isEmpty()) valor = extrairPorRegex(texto, "R\\$\\s*([\\d.,]+)");

        String data = extrairPorRegex(texto, "([\\d]{2}/[\\d]{2}/[\\d]{4})");

        String numero = extrairPorRegex(texto, "NÚMERO\\s*DA\\s*NFS-e[\\s\\S]*?\\n(\\d+)");
        if (numero.isEmpty()) numero = extrairPorRegex(texto, "(\\d{2,})\\s+[\\d]{2}/[\\d]{2}/[\\d]{4}");

        String descricao = extrairPorRegex(texto, "Descrição\\s*do\\s*Serviço[\\s\\S]*?\\n(.*?)\\n");

        return new DadosNotaDTO(emitente, valor, data, numero, descricao);
    }

    private DadosNotaDTO lerNotaProduto(String texto) {
        String emitente = extrairPorRegex(texto, "Recebemos de (.*?) os produtos");
        if (emitente.isEmpty()) emitente = extrairPorRegex(texto, "Chave de Acesso[\\s\\S]*?\\n(.*?)\\s+N[º°]");

        String valor = extrairPorRegex(texto, "Valor Total da Nota[\\s\\S]{0,30}?([\\d]{1,3}(?:\\.[\\d]{3})*,[\\d]{2})");
        String data = extrairPorRegex(texto, "([\\d]{2}/[\\d]{2}/[\\d]{4})");

        String numeroBruto = extrairPorRegex(texto, "N[º°]\\s*([\\d.]{6,20})");
        if (numeroBruto.isEmpty()) numeroBruto = extrairPorRegex(texto, "NF-?e[\\s\\S]{0,15}?([\\d.]{6,20})");

        // procura o formato ex: 000.104.382 pela nota
        if (numeroBruto.isEmpty()) numeroBruto = extrairPorRegex(texto, "\\b(\\d{3}\\.\\d{3}\\.\\d{3})\\b");

        String numero = numeroBruto.replace(".", "").replaceFirst("^0+(?!$)", "");
        String descricao = emitente.isEmpty() ? "Despesa com Produtos" : "Aquisição: " + emitente;

        return new DadosNotaDTO(emitente, valor, data, numero, descricao);
    }

    private String extrairPorRegex(String texto, String padraoRegex) {
        // ignora letras maiúsculas/minúsculas
        Pattern pattern = Pattern.compile(padraoRegex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }
}