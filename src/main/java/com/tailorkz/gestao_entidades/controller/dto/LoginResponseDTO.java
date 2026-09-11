package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record LoginResponseDTO(
        UUID usuarioId,
        String nome,
        String role,
        boolean precisaTrocarSenha,
        String token,
        UUID tenantId,
        String categoria
) {

    public static LoginResponseDTO semToken(UUID usuarioId, String nome, String role, boolean precisaTrocarSenha, UUID tenantId, String categoria) {
        return new LoginResponseDTO(usuarioId, nome, role, precisaTrocarSenha, null, tenantId, categoria);
    }
}