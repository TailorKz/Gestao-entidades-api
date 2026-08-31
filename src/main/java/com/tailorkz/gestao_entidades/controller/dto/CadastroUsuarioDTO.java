package com.tailorkz.gestao_entidades.controller.dto;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.enums.Role;

import java.util.UUID;

public record CadastroUsuarioDTO(
        UUID tenantId,
        String nome,
        String observacoes,
        String login,
        String senha,
        Role role,
        Categoria categoria
) {
}
