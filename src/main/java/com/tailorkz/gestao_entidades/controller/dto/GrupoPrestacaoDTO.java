package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record GrupoPrestacaoDTO(UUID id, String nome, String cor, Integer ordem) {}