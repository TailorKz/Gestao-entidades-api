package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.CadastroUsuarioDTO;
import com.tailorkz.gestao_entidades.controller.dto.UsuarioResponseDTO;
import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/usuarios")
@CrossOrigin(origins = "*")
public class UsuarioController {

    private final UsuarioRepository usuarioRepository;
    private final TenantRepository tenantRepository;
    private final PasswordEncoder passwordEncoder;

    public UsuarioController(UsuarioRepository usuarioRepository, TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping
    public ResponseEntity<?> criarUsuario(@RequestBody CadastroUsuarioDTO dto) {
        // Removemos a validação de email, deixamos apenas a de login
        if (usuarioRepository.findByLogin(dto.login()).isPresent()) {
            return ResponseEntity.badRequest().body("Login já em uso!");
        }

        Tenant tenant = tenantRepository.findById(dto.tenantId())
                .orElseThrow(() -> new RuntimeException("Tenant (Entidade) não encontrado!"));

        Usuario novoUsuario = new Usuario();
        novoUsuario.setNome(dto.nome());
        novoUsuario.setLogin(dto.login());

        // Entra o campo novo no lugar do email
        novoUsuario.setObservacoes(dto.observacoes());

        novoUsuario.setSenhaHash(passwordEncoder.encode(dto.senha()));
        novoUsuario.setRole(dto.role());
        novoUsuario.setCategoria(dto.categoria());
        novoUsuario.setTenant(tenant);
        novoUsuario.setPrecisaTrocarSenha(true);

        usuarioRepository.save(novoUsuario);

        return ResponseEntity.status(HttpStatus.CREATED).body("Usuário criado com sucesso!");
    }

    @GetMapping("/instrutores/{tenantId}")
    public ResponseEntity<List<UsuarioResponseDTO>> listarInstrutores(@PathVariable UUID tenantId) {
        List<UsuarioResponseDTO> instrutores = usuarioRepository.findByTenantIdAndRole(tenantId, Role.INSTRUTOR)
                .stream()
                .map(u -> new UsuarioResponseDTO(
                        u.getId(),
                        u.getNome(),
                        u.getObservacoes(), // Mandando as observações criptografadas abertas para o gestor
                        u.getCategoria() != null ? u.getCategoria().name() : "N/A"
                )).toList();

        return ResponseEntity.ok(instrutores);
    }
}