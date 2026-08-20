package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

// Um "record" no Java gera construtores e getters automaticamente sem precisar de anotações
public record DespesaRequestDTO(
        UUID parcelaId,
        UUID usuarioId,
        BigDecimal valor,
        String dataCompetencia // Formato esperado do Frontend: "2026-08"
) {
}