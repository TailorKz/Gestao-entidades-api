package com.tailorkz.gestao_entidades.controller.dto;

public record MesComprovantesDTO(
        int mes,
        long total,
        long vinculados,
        long pendentes
) {}