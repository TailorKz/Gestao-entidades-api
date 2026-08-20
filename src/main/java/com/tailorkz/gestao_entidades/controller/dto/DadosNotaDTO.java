package com.tailorkz.gestao_entidades.controller.dto;

public record DadosNotaDTO(
        String emitente,
        String valor,
        String data,
        String numero,
        String descricao
) {}