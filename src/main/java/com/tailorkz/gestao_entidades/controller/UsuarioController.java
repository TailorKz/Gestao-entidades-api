package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.CadastroUsuarioDTO;
import com.tailorkz.gestao_entidades.controller.dto.UsuarioResponseDTO;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;
    private final SegurancaService segurancaService;

    public UsuarioController(UsuarioRepository usuarioRepository,
                             TenantRepository tenantRepository,
                             PasswordEncoder passwordEncoder,
                             SegurancaService segurancaService) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
        this.segurancaService = segurancaService;
    }

    @PostMapping
    public ResponseEntity<?> criarUsuario(@RequestBody CadastroUsuarioDTO dto) {
        if (usuarioRepository.findByLogin(dto.login()).isPresent()) {
            return ResponseEntity.badRequest().body("Login já em uso!");
        }

        if (dto.role() == Role.SUPER_ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Não é permitido criar um SUPER_ADMIN por este canal.");
        }

        Tenant tenant;
        if (dto.tenantId() != null) {
            if (!segurancaService.ehSuperAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a outra entidade.");
            }
            tenant = tenantRepository.findById(dto.tenantId())
                    .orElseThrow(() -> new RuntimeException("Tenant (Entidade) não encontrado!"));
        } else {
            tenant = segurancaService.logado().getTenant();
        }

        Usuario novoUsuario = new Usuario();
        novoUsuario.setNome(dto.nome());
        novoUsuario.setLogin(dto.login());
        novoUsuario.setObservacoes(dto.observacoes());
        novoUsuario.setSenhaHash(passwordEncoder.encode(dto.senha()));
        novoUsuario.setRole(dto.role());
        novoUsuario.setCategoria(dto.categoria());
        novoUsuario.setTenant(tenant);
        novoUsuario.setPrecisaTrocarSenha(true);

        usuarioRepository.save(novoUsuario);

        return ResponseEntity.status(HttpStatus.CREATED).body("Usuário criado com sucesso!");
    }

    @GetMapping("/instrutores")
    public ResponseEntity<List<UsuarioResponseDTO>> listarInstrutores(@RequestParam(required = false) String categoria) {
        com.tailorkz.gestao_entidades.domain.enums.Categoria cat = parseCategoria(categoria);
        List<Usuario> instrutores;
        if (segurancaService.ehSuperAdmin()) {
            instrutores = cat != null
                    ? usuarioRepository.findByRoleAndCategoria(Role.INSTRUTOR, cat)
                    : usuarioRepository.findByRole(Role.INSTRUTOR);
        } else {
            UUID tenantId = segurancaService.tenantDoLogado();
            instrutores = cat != null
                    ? usuarioRepository.findByTenantIdAndRoleAndCategoria(tenantId, Role.INSTRUTOR, cat)
                    : usuarioRepository.findByTenantIdAndRole(tenantId, Role.INSTRUTOR);
        }

        List<UsuarioResponseDTO> response = instrutores.stream()
                .map(u -> new UsuarioResponseDTO(
                        u.getId(),
                        u.getNome(),
                        u.getObservacoes(),
                        u.getCategoria() != null ? u.getCategoria().name() : "N/A"
                )).toList();

        return ResponseEntity.ok(response);
    }

    private com.tailorkz.gestao_entidades.domain.enums.Categoria parseCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) return null;
        try {
            return com.tailorkz.gestao_entidades.domain.enums.Categoria.valueOf(categoria.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida: use ESPORTE ou CULTURA.");
        }
    }
}