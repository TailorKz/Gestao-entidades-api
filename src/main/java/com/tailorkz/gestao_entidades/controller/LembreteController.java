package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.Lembrete;
import com.tailorkz.gestao_entidades.domain.model.Tenant;
import com.tailorkz.gestao_entidades.domain.repository.LembreteRepository;
import com.tailorkz.gestao_entidades.domain.repository.TenantRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/lembretes")
@CrossOrigin(origins = "*")
public class LembreteController {

    private final LembreteRepository repository;
    private final TenantRepository tenantRepository;
    private final SegurancaService segurancaService;

    public LembreteController(LembreteRepository repository,
                              TenantRepository tenantRepository,
                              SegurancaService segurancaService) {
        this.repository = repository;
        this.tenantRepository = tenantRepository;
        this.segurancaService = segurancaService;
    }

    @GetMapping
    public ResponseEntity<List<LembreteResponseDTO>> listar(@RequestParam(value = "data", required = false) String data) {
        UUID tenantId = segurancaService.tenantDoLogado();
        LocalDate dia = parseData(data, LocalDate.now());
        List<LembreteResponseDTO> lista = repository.findByTenantIdAndDataOrderByDataAsc(tenantId, dia)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/hoje")
    public ResponseEntity<List<LembreteResponseDTO>> listarHoje() {
        UUID tenantId = segurancaService.tenantDoLogado();
        List<LembreteResponseDTO> lista = repository.findByTenantIdAndDataOrderByDataAsc(tenantId, LocalDate.now())
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @GetMapping("/proximos")
    public ResponseEntity<List<LembreteResponseDTO>> listarProximos(@RequestParam(value = "dias", required = false) String dias) {
        UUID tenantId = segurancaService.tenantDoLogado();
        int qtd = dias == null || dias.isBlank() ? 7 : Integer.parseInt(dias);
        LocalDate limite = LocalDate.now().plusDays(qtd);
        List<LembreteResponseDTO> lista = repository.findByTenantIdAndDataBetweenOrderByDataAsc(
                        tenantId, LocalDate.now(), limite)
                .stream()
                .map(this::paraDTO)
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<LembreteResponseDTO> criar(@RequestBody LembreteRequestDTO dto) {
        if (dto.titulo() == null || dto.titulo().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe o lembrete.");
        }
        LocalDate data = parseData(dto.data(), LocalDate.now());

        Tenant tenant = tenantRepository.findById(segurancaService.tenantDoLogado())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Entidade não encontrada."));

        Lembrete novo = new Lembrete();
        novo.setTitulo(dto.titulo().trim());
        novo.setData(data);
        novo.setTenant(tenant);

        Lembrete salvo = repository.save(novo);
        return ResponseEntity.status(HttpStatus.CREATED).body(paraDTO(salvo));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        Lembrete lembrete = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Lembrete não encontrado."));
        segurancaService.garantirAcessoTenant(lembrete.getTenant().getId());
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private LocalDate parseData(String data, LocalDate padrao) {
        if (data == null || data.isBlank()) return padrao;
        try {
            return LocalDate.parse(data.trim());
        } catch (DateTimeParseException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Data inválida. Use o formato AAAA-MM-DD.");
        }
    }

    private LembreteResponseDTO paraDTO(Lembrete l) {
        return new LembreteResponseDTO(l.getId(), l.getTitulo(), l.getData().toString());
    }
}

record LembreteRequestDTO(String titulo, String data) {}
record LembreteResponseDTO(UUID id, String titulo, String data) {}