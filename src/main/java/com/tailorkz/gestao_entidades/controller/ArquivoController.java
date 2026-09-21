package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.service.ArmazenamentoS3Service;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;

@RestController
@RequestMapping("/arquivos")
@CrossOrigin(origins = "*")
public class ArquivoController {

    private final ArmazenamentoS3Service s3Service;

    public ArquivoController(ArmazenamentoS3Service s3Service) {
        this.s3Service = s3Service;
    }

    @GetMapping("/{nomeArquivo:.+}")
    public ResponseEntity<Void> lerArquivo(@PathVariable String nomeArquivo) {
        try {
            // Pede para o S3 gerar a chave criptografada de 5 minutos
            String urlTemporaria = s3Service.gerarUrlPreAssinada(nomeArquivo);

            // Redireciona o navegador do frontend direto para o Bucket seguro
            return ResponseEntity.status(HttpStatus.FOUND)
                    .location(URI.create(urlTemporaria))
                    .build();
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}