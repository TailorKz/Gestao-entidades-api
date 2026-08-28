package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.LoginRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.LoginResponseDTO;
import com.tailorkz.gestao_entidades.controller.dto.NovaSenhaDTO;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // --- 1. ROTA DE LOGIN ---
    @PostMapping("/login")
    public ResponseEntity<?> fazerLogin(@RequestBody LoginRequestDTO dto) {
        Optional<Usuario> usuarioOpt = usuarioRepository.findByLogin(dto.login());

        if (usuarioOpt.isEmpty()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Usuário não encontrado.");
        }

        Usuario usuario = usuarioOpt.get();

        // Compara a senha digitada com o Hash do banco
        if (!passwordEncoder.matches(dto.senha(), usuario.getSenhaHash())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Senha incorreta.");
        }

        // Se a senha estiver certa, verifica se é o primeiro acesso
        if (usuario.getPrecisaTrocarSenha()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(new LoginResponseDTO(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getRole().name(),
                    true // <--- Avisa o React: "Abra a tela de trocar senha!"
            ));
        }

        // Se não precisa trocar, login normal!
        return ResponseEntity.ok(new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().name(),
                false
        ));
    }

    // --- 2. ROTA DE TROCAR SENHA PROVISÓRIA ---
    @PostMapping("/trocar-senha")
    public ResponseEntity<?> trocarSenha(@RequestBody NovaSenhaDTO dto) {
        Usuario usuario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new RuntimeException("Usuário não encontrado."));

        // Criptografa a nova senha definitiva
        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));

        // Tira o aviso de primeiro acesso!
        usuario.setPrecisaTrocarSenha(false);

        usuarioRepository.save(usuario);

        return ResponseEntity.ok("Senha alterada com sucesso! Você já pode acessar o portal.");
    }
}

