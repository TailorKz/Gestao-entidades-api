package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.Emprestimo;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.repository.EmprestimoRepository;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/emprestimos")
@CrossOrigin(origins = "*")
public class EmprestimoController {

    private final EmprestimoRepository repository;
    private final TenantRepository tenantRepository;
    private final SegurancaService segurancaService;

    public EmprestimoController(EmprestimoRepository repository,
                                TenantRepository tenantRepository,
                                SegurancaService segurancaService) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping
    public ResponseEntity<List<EmprestimoResponseDTO>> listar(@RequestParam("categoria") String categoria) {
        String cat = parseCategoria(categoria);
        List<EmprestimoResponseDTO> lista = repository
                .findByTenantIdAndCategoriaOrderByDataRetiradaDesc(segurancaService.tenantDoLogado(), cat)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/ativos")
    public ResponseEntity<List<EmprestimoResponseDTO>> listarAtivos(@RequestParam("categoria") String categoria) {
        String cat = parseCategoria(categoria);
        List<EmprestimoResponseDTO> lista = repository
                .findByTenantIdAndCategoriaAndDataEntregaIsNullOrderByDataRetiradaDesc(segurancaService.tenantDoLogado(), cat)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/entregues")
    public ResponseEntity<List<EmprestimoResponseDTO>> listarEntregues(@RequestParam("categoria") String categoria) {
        String cat = parseCategoria(categoria);
        List<EmprestimoResponseDTO> lista = repository
                .findByTenantIdAndCategoriaAndDataEntregaIsNotNullOrderByDataEntregaDesc(segurancaService.tenantDoLogado(), cat)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<EmprestimoResponseDTO> criar(@RequestBody EmprestimoRequestDTO dto) {
        if (dto.equipamento() == null || dto.equipamento().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o equipamento retirado.");
        }
        if (dto.nomeRetirante() == null || dto.nomeRetirante().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe quem retirou o equipamento.");
        }
        String cat = parseCategoria(dto.categoria());
        LocalDate dataRetirada = dto.getDataRetirada() != null ? dto.getDataRetirada() : LocalDate.now();

        Tenant tenant = tenantRepository.findById(segurancaService.tenantDoLogado())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidade não encontrada."));

        Emprestimo novo = new Emprestimo();
        novo.setEquipamento(dto.equipamento().trim());
        novo.setNomeRetirante(dto.nomeRetirante().trim());
        novo.setDataRetirada(dataRetirada);
        novo.setCategoria(cat);
        novo.setTenant(tenant);

        Emprestimo salvo = repository.save(novo);
        return ResponseEntity.status(HttpStatus.CREATED).body(paraDTO(salvo));
    }

    @PutMapping("/{id}")
    public ResponseEntity<EmprestimoResponseDTO> editar(@PathVariable UUID id, @RequestBody EmprestimoRequestDTO dto) {
        Emprestimo emprestimo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empréstimo não encontrado."));
        garantireAcesso(emprestimo);

        if (dto.equipamento() == null || dto.equipamento().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o equipamento retirado.");
        }
        if (dto.nomeRetirante() == null || dto.nomeRetirante().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe quem retirou o equipamento.");
        }

        emprestimo.setEquipamento(dto.equipamento().trim());
        emprestimo.setNomeRetirante(dto.nomeRetirante().trim());
        if (dto.getDataRetirada() != null) {
            emprestimo.setDataRetirada(dto.getDataRetirada());
        }
        if (dto.getDataEntrega() != null) {
            emprestimo.setDataEntrega(dto.getDataEntrega());
        }

        Emprestimo salvo = repository.save(emprestimo);
        return ResponseEntity.ok(paraDTO(salvo));
    }

    @PostMapping("/{id}/devolver")
    public ResponseEntity<EmprestimoResponseDTO> devolver(@PathVariable UUID id) {
        Emprestimo emprestimo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empréstimo não encontrado."));
        garantireAcesso(emprestimo);

        if (emprestimo.getDataEntrega() == null) {
            emprestimo.setDataEntrega(LocalDate.now());
            repository.save(emprestimo);
        }
        return ResponseEntity.ok(paraDTO(emprestimo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        Emprestimo emprestimo = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empréstimo não encontrado."));
        garantireAcesso(emprestimo);
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void garantireAcesso(Emprestimo emprestimo) {
        segurancaService.garantirAcessoTenant(emprestimo.getTenant().getId());
    }

    private String parseCategoria(String categoria) {
        if (categoria == null || categoria.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a categoria (ESPORTE ou CULTURA).");
        }
        String cat = categoria.trim().toUpperCase();
        if (!cat.equals("ESPORTE") && !cat.equals("CULTURA")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria inválida.");
        }
        return cat;
    }

    private EmprestimoResponseDTO paraDTO(Emprestimo e) {
        return new EmprestimoResponseDTO(
                e.getId(),
                e.getEquipamento(),
                e.getNomeRetirante(),
                e.getDataRetirada() != null ? e.getDataRetirada().toString() : null,
                e.getDataEntrega() != null ? e.getDataEntrega().toString() : null,
                e.getCategoria()
        );
    }
}

record EmprestimoRequestDTO(String equipamento, String nomeRetirante, String dataRetirada, String dataEntrega, String categoria) {
    LocalDate getDataRetirada() {
        return dataRetirada != null && !dataRetirada.isBlank() ? LocalDate.parse(dataRetirada) : null;
    }
    LocalDate getDataEntrega() {
        return dataEntrega != null && !dataEntrega.isBlank() ? LocalDate.parse(dataEntrega) : null;
    }
}

record EmprestimoResponseDTO(UUID id, String equipamento, String nomeRetirante, String dataRetirada, String dataEntrega, String categoria) {}