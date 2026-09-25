package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DespesaResponseDTO(
        UUID id,
        BigDecimal valor,
        String dataCompetencia,
        String status,
        String nomeInstrutor,
        String nomeEmpresa,
        String observacao,
        String descricao,
        String emitente,
        String documentoFavorecido,
        String tipoDocumento,
        UUID acaoGerrId,
        String acaoGerr,
        boolean temNotaFiscal,
        boolean temComprovante,
        String dataEmissao,
        Integer numeroParcela
) {

    public DespesaResponseDTO(UUID id, BigDecimal valor, String dataCompetencia, String status,
                              String nomeInstrutor, String nomeEmpresa, String observacao, String emitente) {
        this(id, valor, dataCompetencia, status, nomeInstrutor, nomeEmpresa, observacao, null, emitente, null, null, null, null, false, false, null, null);
    }
}