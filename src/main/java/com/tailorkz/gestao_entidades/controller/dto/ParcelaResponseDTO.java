package com.tailorkz.gestao_entidades.controller.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record ParcelaResponseDTO(
        UUID id,
        Integer numero,
        BigDecimal valorInicial,
        BigDecimal saldoAtual,
        String tituloFomento
) {
}