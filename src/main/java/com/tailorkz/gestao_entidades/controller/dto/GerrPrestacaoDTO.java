package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record GerrPrestacaoDTO(
        UUID despesaId,
        String favorecidoNome,
        String numeroDocumento,
        String documentoFavorecido,
        String dataEmissao,
        String dataPagamento,
        String valor,
        String urlNotaFiscal,
        String urlComprovante
) {}