package com.tailorkz.gestao_entidades.domain.service;

import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;

public interface ArmazenamentoArquivoService {
    String armazenar(MultipartFile arquivo, String nomeArquivoOriginal);

    // Nova sobrecarga para receber o arquivo físico temporário
    default String armazenar(Path arquivoFisico, String nomeArquivoOriginal) {
        return null;
    }

    default void deletar(String caminhoArquivo) {}
}