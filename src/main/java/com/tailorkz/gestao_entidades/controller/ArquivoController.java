package com.tailorkz.gestao_entidades.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/arquivos")
@CrossOrigin(origins = "*")
public class ArquivoController {

    // A pasta onde seus arquivos estão sendo salvos
    private final String DIRETORIO_UPLOADS = "uploads";

    @GetMapping("/{nomeArquivo:.+}")
    public ResponseEntity<Resource> lerArquivo(@PathVariable String nomeArquivo) {
        try {
            Path caminhoArquivo = Paths.get(DIRETORIO_UPLOADS).resolve(nomeArquivo).normalize();
            Resource recurso = new UrlResource(caminhoArquivo.toUri());

            if (recurso.exists() && recurso.isReadable()) {
                // Descobre o tipo de arquivo para dizer ao navegador como abrir
                String contentType = "application/octet-stream";
                if (nomeArquivo.toLowerCase().endsWith(".pdf")) {
                    contentType = "application/pdf";
                } else if (nomeArquivo.toLowerCase().endsWith(".png")) {
                    contentType = "image/png";
                } else if (nomeArquivo.toLowerCase().endsWith(".jpg") || nomeArquivo.toLowerCase().endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                }

                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        // 'inline' = abre na aba. Se quisesse forçar download, seria 'attachment'
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + recurso.getFilename() + "\"")
                        .body(recurso);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
}