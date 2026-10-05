package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.service.ArmazenamentoS3Service;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

@RestController
@RequestMapping("/arquivos")
@CrossOrigin(origins = "*")
public class ArquivoController {

    private final ArmazenamentoS3Service s3Service;

    public ArquivoController(ArmazenamentoS3Service s3Service) {
        this.s3Service = s3Service;
    }

    /**
     * Repassa os bytes do arquivo em streaming, em vez de redirecionar para o S3.
     * O bucket não devolve cabeçalhos de CORS, então um XHR do navegador vindo do painel
     * seria bloqueado. Servindo pela própria API, o navegador fala apenas com a origem do
     * backend, que já responde com Access-Control-Allow-Origin.
     */
    @GetMapping("/{nomeArquivo:.+}")
    public ResponseEntity<StreamingResponseBody> lerArquivo(@PathVariable String nomeArquivo) {
        ResponseInputStream<GetObjectResponse> objeto;
        try {
            objeto = s3Service.abrirLeitura(nomeArquivo);
        } catch (AwsServiceException e) {
            if (e.statusCode() == HttpStatus.NOT_FOUND.value()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Arquivo não encontrado.");
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao buscar o arquivo.");
        }

        String contentType = objeto.response().contentType();
        MediaType tipo = contentType != null && !contentType.isBlank()
                ? MediaType.parseMediaType(contentType)
                : MediaType.APPLICATION_PDF;
        long tamanho = objeto.response().contentLength() == null ? -1 : objeto.response().contentLength();

        StreamingResponseBody corpo = (OutputStream saida) -> copiar(objeto, saida);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(tipo);
        headers.setContentDisposition(ContentDisposition.inline().build());
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.CONTENT_DISPOSITION);
        if (tamanho > 0) {
            headers.setContentLength(tamanho);
        }
        headers.setCacheControl("no-store");

        return new ResponseEntity<>(corpo, headers, HttpStatus.OK);
    }

    private void copiar(InputStream origem, OutputStream destino) throws IOException {
        try (InputStream entrada = origem) {
            entrada.transferTo(destino);
        }
    }
}
