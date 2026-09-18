package com.tailorkz.gestao_entidades.controller.dto;

public record DadosNotaDTO(
        String emitente,
        String valor,
        String data,
        String numero,
        String descricao,
        String documento
) {

    public DadosNotaDTO(String emitente, String valor, String data, String numero, String descricao) {
        this(emitente, valor, data, numero, descricao, "");
    }
}