package com.tailorkz.gestao_entidades.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tailorkz.gestao_entidades.controller.dto.DadosNotaDTO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class OcrService {

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    @Value("${gemini.api.url:https://generativelanguage.googleapis.com}")
    private String geminiApiUrl;

    @Value("${gemini.api.modelo:gemini-3.5-flash}")
    private String geminiModelo;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public DadosNotaDTO extrairDadosPdf(MultipartFile arquivo) {
        try (PDDocument document = PDDocument.load(arquivo.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String textoPdf = stripper.getText(document).replaceAll("\r", "");

            // 1. DETECÇÃO DE DOCUMENTO DIGITALIZADO (IMAGEM)
            if (textoPdf.trim().isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Documento digitalizado ou sem texto legível. Por favor, preencha os dados manualmente."
                );
            }

            System.out.println("=== O QUE O ROBO LEU DO PDF ===");
            System.out.println(textoPdf);

            String textoUpper = textoPdf.toUpperCase();
            DadosNotaDTO dadosLocais;

            if (textoUpper.contains("DANFSE") || textoUpper.contains("PRESTADOR") || textoUpper.contains("NFS-E")) {
                dadosLocais = lerNotaServicoUnificada(textoPdf);
            } else {
                dadosLocais = lerNotaProduto(textoPdf);
            }

            // 2. CONDIÇÃO ATUALIZADA PARA O FALLBACK (Pegando o 0,00)
            boolean precisaIA = dadosLocais.valor().isEmpty()
                    || dadosLocais.valor().equals("0,00")
                    || dadosLocais.numero().isEmpty()
                    || dadosLocais.emitente().isEmpty();

            if (precisaIA) {
                System.out.println("  Dados incompletos ou valor 0,00. Acionando a IA do Gemini como Fallback...");
                return extrairComIA(textoPdf, dadosLocais);
            }

            System.out.println("  Leitura concluída via Regex (Custo: Zero).");
            return dadosLocais;

        } catch (ResponseStatusException e) {
            throw e; // Repassa a exceção de documento digitalizado para o Controller
        } catch (Exception e) {
            throw new RuntimeException("Falha ao processar o PDF", e);
        }
    }

    private DadosNotaDTO extrairComIA(String texto, DadosNotaDTO fallbackLocal) {
        if (geminiApiKey == null || geminiApiKey.isEmpty() || geminiApiKey.contains("COLE_SUA_CHAVE")) {
            System.out.println("  Chave do Gemini vazia. Abortando Fallback.");
            return fallbackLocal;
        }

        try {
            String url = geminiApiUrl + "/v1beta/models/" + geminiModelo + ":generateContent?key=" + geminiApiKey;
            String textoSeguro = texto.replace("\n", " ").replace("\r", "").replaceAll("[\\x00-\\x1F]", "");

            String prompt = "Você é um assistente de extração de notas fiscais. Extraia os dados do texto a seguir. " +
                    "Devolva APENAS um JSON válido e estrito. Não inclua markdown, blocos de código ou explicações. " +
                    "Formato exigido: {\"emitente\": \"nome\", \"valor\": \"1500,00\", \"data\": \"YYYY-MM-DD\", \"numero\": \"numero puro\", \"descricao\": \"\"}. " +
                    "Se um dado não existir, deixe a string vazia. Texto: " + textoSeguro;

            java.util.Map<String, Object> bodyMap = java.util.Map.of(
                    "contents", java.util.List.of(
                            java.util.Map.of("parts", java.util.List.of(
                                    java.util.Map.of("text", prompt)
                            ))
                    )
            );
            String requestBody = objectMapper.writeValueAsString(bodyMap);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(org.springframework.http.MediaType.APPLICATION_JSON);
            org.springframework.http.HttpEntity<String> entity = new org.springframework.http.HttpEntity<>(requestBody, headers);

            // LOOP DE SEGURANÇA: Tenta até 3 vezes antes de desistir
            int maxTentativas = 3;
            for (int tentativa = 1; tentativa <= maxTentativas; tentativa++) {
                try {
                    String response = restTemplate.postForObject(url, entity, String.class);
                    com.fasterxml.jackson.databind.JsonNode rootNode = objectMapper.readTree(response);

                    String respostaIA = rootNode.path("candidates").get(0).path("content").path("parts").get(0).path("text").asText();
                    respostaIA = respostaIA.replace("```json", "").replace("```", "").trim();
                    com.fasterxml.jackson.databind.JsonNode notaJson = objectMapper.readTree(respostaIA);

                    System.out.println("  Dados recuperados com sucesso pela IA!");
                    return new DadosNotaDTO(
                            notaJson.path("emitente").asText(),
                            notaJson.path("valor").asText(),
                            notaJson.path("data").asText(),
                            notaJson.path("numero").asText(),
                            notaJson.path("descricao").asText()
                    );

                } catch (org.springframework.web.client.HttpServerErrorException.ServiceUnavailable |
                         org.springframework.web.client.HttpServerErrorException.GatewayTimeout e) {
                    System.out.println("  [API Ocupada 503] Tentativa " + tentativa + " falhou. Retentando em 2s...");
                    if (tentativa == maxTentativas) {
                        System.err.println("  Desistindo após " + maxTentativas + " tentativas.");
                        return fallbackLocal;
                    }
                    Thread.sleep(2000); // Pausa a execução por 2 segundos antes do próximo loop
                }
            }

        } catch (Exception e) {
            System.err.println("Erro geral ao consultar a IA: " + e.getMessage());
        }

        return fallbackLocal;
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