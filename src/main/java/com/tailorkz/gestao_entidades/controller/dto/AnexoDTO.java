package com.tailorkz.gestao_entidades.controller.dto;

import java.util.UUID;

// O record fica limpo aqui no final, sem a chave extra "}" depois dele!
public record AnexoDTO(UUID id, String tipo, String nomeOriginal, String caminhoReal) {
}
