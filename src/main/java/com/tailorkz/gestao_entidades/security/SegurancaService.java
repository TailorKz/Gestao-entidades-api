package com.tailorkz.gestao_entidades.security;

import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
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

    public boolean ehGestor() {
        return logado().getRole() == Role.SUPER_ADMIN
                || logado().getRole() == Role.GESTOR_ENTIDADE;
    }

    public boolean ehInstrutor() {
        return logado().getRole() == Role.INSTRUTOR;
    }

    public void garantirEhGestor(String mensagem) {
        if (!ehGestor()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagem);
        }
    }

    public void garantirProprioOuGestor(UUID usuarioId, UUID tenantId, String mensagemParaInstrutor) {
        Usuario logado = logado();
        if (logado.getRole() == Role.INSTRUTOR && !logado.getId().equals(usuarioId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, mensagemParaInstrutor);
        }
        garantirAcessoTenant(tenantId);
    }

    public void garantirAcessoDespesa(Despesa despesa) {
        Usuario logado = logado();
        if (logado.getRole() == Role.INSTRUTOR
                && (despesa.getUsuario() == null || !logado.getId().equals(despesa.getUsuario().getId()))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado.");
        }
        garantirAcessoTenant(despesa.getParcela().getFomento().getTenant().getId());
    }
}