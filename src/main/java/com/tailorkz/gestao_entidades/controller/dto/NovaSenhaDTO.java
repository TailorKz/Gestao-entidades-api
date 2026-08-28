package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

public record NovaSenhaDTO(UUID usuarioId, String novaSenha) {
}
