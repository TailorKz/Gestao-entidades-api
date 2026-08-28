package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
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
    private final PasswordEncoder passwordEncoder; // Nosso encriptador BCrypt!

    public UsuarioController(UsuarioRepository usuarioRepository, TenantRepository tenantRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.tenantRepository = tenantRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ROTA 1: CADASTRAR INSTRUTOR OU GESTOR
    @PostMapping
    public ResponseEntity<?> criarUsuario(@RequestBody CadastroUsuarioDTO dto) {
        // Valida se o email ou login já existem
        if (usuarioRepository.findByEmail(dto.email()).isPresent()) {
            return ResponseEntity.badRequest().body("E-mail já cadastrado!");
        }
        if (usuarioRepository.findByLogin(dto.login()).isPresent()) {
            return ResponseEntity.badRequest().body("Login já em uso!");
        }

        // Busca o Tenant (INDACI)
        Tenant tenant = tenantRepository.findById(dto.tenantId())
                .orElseThrow(() -> new RuntimeException("Tenant (Entidade) não encontrado!"));

        Usuario novoUsuario = new Usuario();
        novoUsuario.setNome(dto.nome());
        novoUsuario.setEmail(dto.email());
        novoUsuario.setLogin(dto.login());

        // Criptografa a senha antes de salvar!
        novoUsuario.setSenhaHash(passwordEncoder.encode(dto.senha()));

        novoUsuario.setRole(dto.role());
        novoUsuario.setCategoria(dto.categoria()); // Ex: ESPORTE ou CULTURA
        novoUsuario.setTenant(tenant);

        usuarioRepository.save(novoUsuario);

        return ResponseEntity.status(HttpStatus.CREATED).body("Usuário criado com sucesso!");
    }

    // --- ROTA 2: LISTAR INSTRUTORES (Para a tela do Admin) ---
    @GetMapping("/instrutores/{tenantId}")
    public ResponseEntity<List<UsuarioResponseDTO>> listarInstrutores(@PathVariable UUID tenantId) {
        List<UsuarioResponseDTO> instrutores = usuarioRepository.findByTenantIdAndRole(tenantId, Role.INSTRUTOR)
                .stream()
                .map(u -> new UsuarioResponseDTO(
                        u.getId(),
                        u.getNome(),
                        u.getEmail(),
                        u.getCategoria() != null ? u.getCategoria().name() : "N/A"
                )).toList();

        return ResponseEntity.ok(instrutores);
    }
}

record CadastroUsuarioDTO(
        UUID tenantId,
        String nome,
        String email,
        String login,
        String senha,
        Role role,
        Categoria categoria
) {}

record UsuarioResponseDTO(
        UUID id,
        String nome,
        String email,
        String categoria
) {}