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
        String emitente,
        String documentoFavorecido,
        boolean temNotaFiscal,
        boolean temComprovante
) {

    public DespesaResponseDTO(UUID id, BigDecimal valor, String dataCompetencia, String status,
                              String nomeInstrutor, String nomeEmpresa, String observacao, String emitente) {
        this(id, valor, dataCompetencia, status, nomeInstrutor, nomeEmpresa, observacao, emitente, null, false, false);
    }
}