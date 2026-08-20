package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record DespesaResponseDTO(
        UUID id,
        BigDecimal valor,
        String dataCompetencia,
        String status,
        String nomeInstrutor
) {
}