package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ComprovanteDTO(
        UUID id,
        String nomeArquivo,
        BigDecimal valor,
        String dataPagamento,
        String favorecido,
        String documentoFavorecido,
        String autenticacao,
        UUID despesaId,
        String despesaDescricao,
        boolean temNotaFiscal,
        String chaveS3
) {}