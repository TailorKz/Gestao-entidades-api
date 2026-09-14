package com.tailorkz.gestao_entidades.domain.service;

import org.springframework.web.multipart.MultipartFile;

public interface ArmazenamentoArquivoService {

    // Recebe o arquivo e devolve o link/caminho de onde ele foi salvo
    String armazenar(MultipartFile arquivo, String nomeArquivoOriginal);

    // Remove o arquivo físico do disco
    default void deletar(String caminhoArquivo) {
        // Sem armazenamento relativo, nada a fazer
    }
}