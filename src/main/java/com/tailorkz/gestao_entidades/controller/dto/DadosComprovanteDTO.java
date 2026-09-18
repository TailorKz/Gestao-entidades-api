package com.tailorkz.gestao_entidades.controller.dto;

public record DadosComprovanteDTO(
        String valor,
        String data,
        String favorecido,
        String documento,
        String autenticacao
) {}