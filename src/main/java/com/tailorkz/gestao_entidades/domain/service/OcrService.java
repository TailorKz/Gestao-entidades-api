package com.tailorkz.gestao_entidades.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorkz.gestao_entidades.controller.dto.DadosNotaDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DadosNotaDTO extrairDadosPdf(MultipartFile arquivo) {
        try (PDDocument document = PDDocument.load(arquivo.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String textoPdf = stripper.getText(document).replaceAll("\r", "");

            System.out.println("=== O QUE O ROBO LEU DO PDF ===");
            System.out.println(textoPdf);

            String textoUpper = textoPdf.toUpperCase();
            DadosNotaDTO dadosLocais;

            if (textoUpper.contains("DANFSE") || textoUpper.contains("PRESTADOR") || textoUpper.contains("NFS-E")) {
                dadosLocais = lerNotaServicoUnificada(textoPdf); // Rota única para qualquer Prefeitura
            } else {
                dadosLocais = lerNotaProduto(textoPdf); // Rota NF-e (Produto/MercadoLivre)
            }

            if (dadosLocais.valor().isEmpty() || dadosLocais.numero().isEmpty() || dadosLocais.emitente().isEmpty()) {
                System.out.println("⚠️ Regex local falhou. Acionando a IA do Gemini como Fallback...");
                return extrairComIA(textoPdf, dadosLocais);
            }

            System.out.println("✅ Leitura concluída via Regex (Custo: Zero).");
            return dadosLocais;

        } catch (Exception e) {
            throw new RuntimeException("Falha ao processar o PDF", e);
        }
    }

    private DadosNotaDTO extrairComIA(String texto, DadosNotaDTO fallbackLocal) {
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.contains("COLE_SUA_CHAVE")) {
            System.out.println("❌ Chave do Gemini inválida ou vazia. Abortando Fallback.");
            return fallbackLocal;
        }

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + geminiApiKey;

            // Tratamento pesado para impedir que o PDF quebre o JSON do Google
            String textoSeguro = texto.replace("\"", "\\\"").replace("\n", " ").replace("\r", "").replaceAll("[\\x00-\\x1F]", "");

            String prompt = "Extraia os dados desta nota fiscal. Devolva APENAS um JSON válido. " +
                    "Formato: {\"emitente\": \"\", \"valor\": \"1500,00\", \"data\": \"YYYY-MM-DD\", \"numero\": \"numero puro\", \"descricao\": \"\"}. " +
                    "Texto: " + textoSeguro;

            String requestBody = "{ \"contents\": [{ \"parts\": [{\"text\": \"" + prompt + "\"}] }] }";

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<String> entity = new HttpEntity<>(requestBody, headers);

            String response = restTemplate.postForObject(url, entity, String.class);

            JsonNode rootNode = objectMapper.readTree(response);
            String respostaIA = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
            respostaIA = respostaIA.replace("```json", "").replace("```", "").trim();

            JsonNode notaJson = objectMapper.readTree(respostaIA);

            System.out.println("🧠 Dados recuperados com sucesso pela IA!");
            return new DadosNotaDTO(
                    notaJson.path("emitente").asText(),
                    notaJson.path("valor").asText(),
                    notaJson.path("data").asText(),
                    notaJson.path("numero").asText(),
                    notaJson.path("descricao").asText()
            );

        } catch (Exception e) {
            System.err.println("Erro ao consultar a IA: " + e.getMessage());
            return fallbackLocal;
        }
    }

    // --- REGRA UNIFICADA DE PREFEITURA (V1.0 e V2.0) ---
    private DadosNotaDTO lerNotaServicoUnificada(String texto) {
        String emitenteBruto = extrairPorRegex(texto, "Nome\\s*/\\s*Nome\\s*Empresarial[^\\n]*\\n([^\\n]+(?:\\n[^\\n]+)?)");

        String emitente = emitenteBruto.replaceAll("\\S+@\\S+", "")
                .replaceAll("(?i)e-?mail", "")
                .replaceAll("[0-9.-]", "")
                .trim();

        if (emitente.contains("\n")) emitente = emitente.split("\n")[0].trim();
        if (emitente.isEmpty()) emitente = emitenteBruto.split("\n")[0].trim().replaceAll("[0-9.-]", "");

        // REGRA BLINDADA PARA ACENTOS (Líquido, Total ou Serviço)
        String valor = extrairPorRegex(texto, "VALOR\\s*L[ÍíIi]QUIDO\\s*DA\\s*NFS-e[\\s\\S]*?R\\$\\s*([\\d.,]+)");
        if (valor.isEmpty()) valor = extrairPorRegex(texto, "VALOR\\s*DO\\s*SERVIÇO[\\s\\S]*?R\\$\\s*([\\d.,]+)");
        if (valor.isEmpty()) valor = extrairPorRegex(texto, "VALOR\\s*TOTAL[\\s\\S]*?R\\$\\s*([\\d.,]+)");

        String data = extrairPorRegex(texto, "([\\d]{2}/[\\d]{2}/[\\d]{4})");

        // REGRA BLINDADA PARA ACENTOS (Número)
        String numero = extrairPorRegex(texto, "N[ÚúUu]MERO\\s*DA\\s*NFS-e[^\\n]*\\n(\\d+)");
        if (numero.isEmpty()) numero = extrairPorRegex(texto, "(\\d{2,})\\s+[\\d]{2}/[\\d]{2}/[\\d]{4}");

        String descricao = extrairPorRegex(texto, "DESCRIÇÃO\\s*DO\\s*SERVIÇO[\\s\\S]*?\\n(.*?)\\n");

        return new DadosNotaDTO(emitente, valor, data, numero, descricao);
    }

    // -- O MOTOR DE BUSCA (Ajustado para entender acentos) --
    private String extrairPorRegex(String texto, String padraoRegex) {
        // A MÁGICA: UNICODE_CASE ensina o Java que Ú e ú são a mesma letra!
        Pattern pattern = Pattern.compile(padraoRegex, Pattern.DOTALL | Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(texto);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return "";
    }

    private DadosNotaDTO lerNotaProduto(String texto) {
        String emitente = extrairPorRegex(texto, "Recebemos de (.*?) os produtos");
        if (emitente.isEmpty()) emitente = extrairPorRegex(texto, "Chave de Acesso[\\s\\S]*?\\n(.*?)\\s+N[º°]");

        String valor = extrairPorRegex(texto, "Valor Total da Nota[\\s\\S]{0,30}?([\\d]{1,3}(?:\\.[\\d]{3})*,[\\d]{2})");
        String data = extrairPorRegex(texto, "([\\d]{2}/[\\d]{2}/[\\d]{4})");

        String numeroBruto = extrairPorRegex(texto, "N[º°]\\s*([\\d.]{6,20})");
        if (numeroBruto.isEmpty()) numeroBruto = extrairPorRegex(texto, "NF-?e[\\s\\S]{0,15}?([\\d.]{6,20})");
        if (numeroBruto.isEmpty()) numeroBruto = extrairPorRegex(texto, "\\b(\\d{3}\\.\\d{3}\\.\\d{3})\\b");

        String numero = numeroBruto.replace(".", "").replaceFirst("^0+(?!$)", "");
        String descricao = emitente.isEmpty() ? "Despesa com Produtos" : "Aquisição: " + emitente;

        return new DadosNotaDTO(emitente, valor, data, numero, descricao);
    }

}