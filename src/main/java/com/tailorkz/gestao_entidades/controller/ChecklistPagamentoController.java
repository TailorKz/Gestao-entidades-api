package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.enums.Role;
import com.tailorkz.gestao_entidades.domain.model.ChecklistPagamento;
import com.tailorkz.gestao_entidades.domain.model.Usuario;
import com.tailorkz.gestao_entidades.domain.repository.ChecklistPagamentoRepository;
import com.tailorkz.gestao_entidades.domain.repository.UsuarioRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/checklist-pagamentos")
@CrossOrigin(origins = "*")
public class ChecklistPagamentoController {

    private final ChecklistPagamentoRepository repository;
    private final UsuarioRepository usuarioRepository;
    private final SegurancaService segurancaService;

    public ChecklistPagamentoController(ChecklistPagamentoRepository repository,
                                        UsuarioRepository usuarioRepository,
                                        SegurancaService segurancaService) {
        this.repository = repository;
        this.usuarioRepository = usuarioRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping
    public ResponseEntity<List<ChecklistPagamentoDTO>> listar(@RequestParam(value = "ano", required = false) Integer ano,
                                                              @RequestParam(value = "mes", required = false) Integer mes) {
        segurancaService.garantirEhGestor("Somente gestores podem acessar o checklist de pagamentos.");

        int anoRef = ano == null ? LocalDateTime.now().getYear() : ano;
        int mesRef = mes == null ? LocalDateTime.now().getMonthValue() : mes;
        validarCompetencia(anoRef, mesRef);

        List<ChecklistPagamentoDTO> lista = repository.findByTenantIdAndAnoAndMes(segurancaService.tenantDoLogado(), anoRef, mesRef)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PutMapping
    public ResponseEntity<ChecklistPagamentoDTO> salvar(@RequestBody ChecklistPagamentoRequestDTO dto) {
        segurancaService.garantirEhGestor("Somente gestores podem alterar o checklist de pagamentos.");

        if (dto == null || dto.usuarioId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o instrutor.");
        }
        validarCompetencia(dto.ano(), dto.mes());

        Usuario instrutor = usuarioRepository.findById(dto.usuarioId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Instrutor não encontrado."));
        if (!Role.INSTRUTOR.equals(instrutor.getRole())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O usuário selecionado não é instrutor.");
        }
        segurancaService.garantirAcessoTenant(instrutor.getTenant().getId());

        ChecklistPagamento registro = repository
                .findByTenantIdAndUsuario_IdAndAnoAndMes(
                        instrutor.getTenant().getId(), instrutor.getId(), dto.ano(), dto.mes())
                .orElseGet(() -> ChecklistPagamento.builder()
                        .tenant(instrutor.getTenant())
                        .usuario(instrutor)
                        .ano(dto.ano())
                        .mes(dto.mes())
                        .build());

        registro.setEntregouDocumentos(Boolean.TRUE.equals(dto.entregouDocumentos()));
        registro.setPagamentoFeito(Boolean.TRUE.equals(dto.pagamentoFeito()));

        return ResponseEntity.ok(paraDTO(repository.save(registro)));
    }

    private void validarCompetencia(Integer ano, Integer mes) {
        if (ano == null || mes == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o mês de referência.");
        }
        if (ano < 2000 || ano > 2100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Ano inválido.");
        }
        if (mes < 1 || mes > 12) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Mês inválido.");
        }
    }

    private ChecklistPagamentoDTO paraDTO(ChecklistPagamento c) {
        Usuario u = c.getUsuario();
        return new ChecklistPagamentoDTO(
                c.getId(),
                u.getId(),
                u.getNome(),
                u.getCategoria() == null ? null : u.getCategoria().name(),
                c.getAno(),
                c.getMes(),
                Boolean.TRUE.equals(c.getEntregouDocumentos()),
                Boolean.TRUE.equals(c.getPagamentoFeito()),
                c.getAtualizadoEm() == null ? null : c.getAtualizadoEm().toString());
    }
}

record ChecklistPagamentoRequestDTO(UUID usuarioId, Integer ano, Integer mes,
                                    Boolean entregouDocumentos, Boolean pagamentoFeito) {}

record ChecklistPagamentoDTO(UUID id, UUID usuarioId, String nome, String categoria,
                             Integer ano, Integer mes,
                             boolean entregouDocumentos, boolean pagamentoFeito,
                             String atualizadoEm) {}
