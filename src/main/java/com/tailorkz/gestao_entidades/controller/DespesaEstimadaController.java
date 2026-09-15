package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.DespesaEstimada;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.DespesaEstimadaRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import com.tailorkz.gestao_entidades.security.SegurancaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/estimativas")
@CrossOrigin(origins = "*")
public class DespesaEstimadaController {

    private final DespesaEstimadaRepository repository;
    private final ParcelaRepository parcelaRepository;
    private final SegurancaService segurancaService;

    public DespesaEstimadaController(DespesaEstimadaRepository repository,
                                     ParcelaRepository parcelaRepository,
                                     SegurancaService segurancaService) {
        this.repository = repository;
        this.parcelaRepository = parcelaRepository;
        this.segurancaService = segurancaService;
    }

    @PostMapping
    public ResponseEntity<DespesaEstimadaResponseDTO> criar(@RequestBody DespesaEstimadaRequestDTO dto) {
        Parcela parcela = validarParcela(dto.parcelaId());

        DespesaEstimada nova = new DespesaEstimada();
        nova.setDescricao(dto.descricao());
        nova.setValor(dto.valor());
        nova.setParcela(parcela);

        DespesaEstimada salva = repository.save(nova);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DespesaEstimadaResponseDTO(salva.getId(), salva.getDescricao(), salva.getValor()));
    }

    @GetMapping("/parcela/{parcelaId}")
    public ResponseEntity<List<DespesaEstimadaResponseDTO>> listarPorParcela(@PathVariable UUID parcelaId) {
        Parcela parcela = validarParcela(parcelaId);

        List<DespesaEstimadaResponseDTO> lista = repository.findByParcelaId(parcela.getId()).stream()
                .map(e -> new DespesaEstimadaResponseDTO(e.getId(), e.getDescricao(), e.getValor()))
                .toList();
        return ResponseEntity.ok(lista);
    }

    @PutMapping("/{id}")
    public ResponseEntity<DespesaEstimadaResponseDTO> editar(@PathVariable UUID id, @RequestBody DespesaEstimadaRequestDTO dto) {
        DespesaEstimada estimativa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estimativa não encontrada."));
        validarParcela(estimativa.getParcela().getId());

        if (dto.descricao() == null || dto.descricao().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe a descrição do gasto.");
        }
        if (dto.valor() == null || dto.valor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor do gasto deve ser maior que zero.");
        }

        estimativa.setDescricao(dto.descricao().trim());
        estimativa.setValor(dto.valor());

        DespesaEstimada salva = repository.save(estimativa);
        return ResponseEntity.ok(new DespesaEstimadaResponseDTO(salva.getId(), salva.getDescricao(), salva.getValor()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        DespesaEstimada estimativa = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Estimativa não encontrada."));
        validarParcela(estimativa.getParcela().getId());
        repository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Parcela validarParcela(UUID parcelaId) {
        Parcela parcela = parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcela não encontrada."));
        segurancaService.garantirAcessoTenant(parcela.getFomento().getTenant().getId());
        return parcela;
    }
}

record DespesaEstimadaRequestDTO(String descricao, BigDecimal valor, UUID parcelaId) {}
record DespesaEstimadaResponseDTO(UUID id, String descricao, BigDecimal valor) {}