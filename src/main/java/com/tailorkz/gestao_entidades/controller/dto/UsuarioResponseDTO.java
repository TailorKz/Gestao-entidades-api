package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String login,
        String observacoes,
        String categoria,
        String role
) {
}
