package com.tailorkz.gestao_entidades.security;

import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@Service
public class SegurancaService {

    public Usuario logado() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Usuario usuario) {
            return usuario;
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuário não autenticado.");
    }

    public boolean ehSuperAdmin() {
        return Role.SUPER_ADMIN.equals(logado().getRole());
    }

    public UUID tenantDoLogado() {
        return logado().getTenant().getId();
    }

    public void garantirAcessoTenant(UUID tenantId) {
        if (tenantId == null) return;
        if (ehSuperAdmin()) return;
        if (!tenantId.equals(tenantDoLogado())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a outra entidade.");
        }
    }
}