package com.tailorkz.gestao_entidades.controller.dto;

import com.tailorkz.gestao_entidades.domain.enums.TipoDocumentoGerr;

public record DadosNotaDTO(
        String emitente,
        String valor,
        String data,
        String numero,
        String descricao,
        String documento,
        TipoDocumentoGerr tipoDocumento
) {

    public DadosNotaDTO(String emitente, String valor, String data, String numero, String descricao) {
        this(emitente, valor, data, numero, descricao, "", null);
    }

    public DadosNotaDTO(String emitente, String valor, String data, String numero, String descricao, String documento) {
        this(emitente, valor, data, numero, descricao, documento, null);
    }
}