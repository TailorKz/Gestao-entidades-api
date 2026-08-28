package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record LoginResponseDTO(UUID usuarioId, String nome, String role, Boolean precisaTrocarSenha) {
}
