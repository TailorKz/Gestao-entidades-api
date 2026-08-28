package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String categoria
) {
}
