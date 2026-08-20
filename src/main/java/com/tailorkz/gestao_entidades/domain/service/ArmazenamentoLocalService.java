package com.tailorkz.gestao_entidades.domain.service;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ArmazenamentoLocalService implements ArmazenamentoArquivoService {

    // Define a pasta onde os arquivos serão salvos fisicamente
    private final Path diretorioRaiz = Paths.get("uploads");

    public ArmazenamentoLocalService() {
        try {
            // Ao iniciar a API, o Java verifica se a pasta "uploads" existe. Se não, ele cria.
            Files.createDirectories(diretorioRaiz);
        } catch (Exception e) {
            throw new RuntimeException("Não foi possível inicializar o diretório de uploads", e);
        }
    }

    @Override
    public String armazenar(MultipartFile arquivo, String nomeArquivoOriginal) {
        try {
            // Gera um ID único antes do nome para evitar que um arquivo substitua o outro
            String nomeSeguro = UUID.randomUUID().toString() + "_" + nomeArquivoOriginal;

            Path destino = this.diretorioRaiz.resolve(nomeSeguro);

            // Copia o arquivo da requisição web para a pasta física
            Files.copy(arquivo.getInputStream(), destino);

            return destino.toString(); // Retorna "uploads\nome-do-arquivo.pdf"

        } catch (Exception e) {
            throw new RuntimeException("Falha ao armazenar o arquivo no disco.", e);
        }
    }
}