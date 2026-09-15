package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.LoginRequestDTO;
import com.tailorkz.gestao_entidades.controller.dto.LoginResponseDTO;
import com.tailorkz.gestao_entidades.controller.dto.NovaSenhaDTO;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.security.JwtService;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/auth")
@CrossOrigin(origins = "*")
public class AuthController {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final SegurancaService segurancaService;

    public AuthController(UsuarioRepository usuarioRepository,
                          PasswordEncoder passwordEncoder,
                          JwtService jwtService,
                          SegurancaService segurancaService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.segurancaService = segurancaService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> fazerLogin(@RequestBody LoginRequestDTO dto) {
        Usuario usuario = usuarioRepository.findByLogin(dto.login())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta."));

        if (!passwordEncoder.matches(dto.senha(), usuario.getSenhaHash())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha incorreta.");
        }

        if (usuario.getPrecisaTrocarSenha()) {
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(LoginResponseDTO.semToken(
                    usuario.getId(),
                    usuario.getNome(),
                    usuario.getRole().name(),
                    true,
                    usuario.getTenant().getId(),
                    usuario.getCategoria() != null ? usuario.getCategoria().name() : null
            ));
        }

        String token = jwtService.gerarToken(
                usuario.getId(),
                usuario.getRole().name(),
                usuario.getTenant().getId(),
                usuario.getNome(),
                usuario.getCategoria() != null ? usuario.getCategoria().name() : ""
        );

        return ResponseEntity.ok(new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().name(),
                false,
                token,
                usuario.getTenant().getId(),
                usuario.getCategoria() != null ? usuario.getCategoria().name() : null
        ));
    }

    @PostMapping("/trocar-senha")
    public ResponseEntity<?> trocarSenha(@RequestBody NovaSenhaDTO dto) {
        if (dto.novaSenha() == null || dto.novaSenha().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a nova senha.");
        }
        if (dto.novaSenha().length() < 6) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A nova senha deve ter no mínimo 6 caracteres.");
        }

        Usuario usuario = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuário não encontrado."));

        usuario.setSenhaHash(passwordEncoder.encode(dto.novaSenha()));
        usuario.setPrecisaTrocarSenha(false);
        usuarioRepository.save(usuario);

        String token = jwtService.gerarToken(
                usuario.getId(),
                usuario.getRole().name(),
                usuario.getTenant().getId(),
                usuario.getNome(),
                usuario.getCategoria() != null ? usuario.getCategoria().name() : ""
        );

        return ResponseEntity.ok(new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().name(),
                false,
                token,
                usuario.getTenant().getId(),
                usuario.getCategoria() != null ? usuario.getCategoria().name() : null
        ));
    }

    @GetMapping("/me")
    public ResponseEntity<LoginResponseDTO> eu() {
        Usuario usuario = segurancaService.logado();
        return ResponseEntity.ok(new LoginResponseDTO(
                usuario.getId(),
                usuario.getNome(),
                usuario.getRole().name(),
                false,
                null,
                usuario.getTenant().getId(),
                usuario.getCategoria() != null ? usuario.getCategoria().name() : null
        ));
    }
}