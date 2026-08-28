package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.DespesaEstimada;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.DespesaEstimadaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/estimativas")
@CrossOrigin(origins = "*")
public class DespesaEstimadaController {

    private final DespesaEstimadaRepository repository;

    public DespesaEstimadaController(DespesaEstimadaRepository repository) {
        this.repository = repository;
    }

    // 1. Criar uma nova estimativa
    @PostMapping
    public ResponseEntity<DespesaEstimadaResponseDTO> criar(@RequestBody DespesaEstimadaRequestDTO dto) {
        DespesaEstimada nova = new DespesaEstimada();
        nova.setDescricao(dto.descricao());
        nova.setValor(dto.valor());

        Parcela p = new Parcela();
        p.setId(dto.parcelaId());
        nova.setParcela(p);

        DespesaEstimada salva = repository.save(nova);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new DespesaEstimadaResponseDTO(salva.getId(), salva.getDescricao(), salva.getValor()));
    }

    // 2. Listar estimativas de uma parcela
    @GetMapping("/parcela/{parcelaId}")
    public ResponseEntity<List<DespesaEstimadaResponseDTO>> listarPorParcela(@PathVariable UUID parcelaId) {
        List<DespesaEstimadaResponseDTO> lista = repository.findByParcelaId(parcelaId).stream()
                .map(e -> new DespesaEstimadaResponseDTO(e.getId(), e.getDescricao(), e.getValor()))
                .toList();
        return ResponseEntity.ok(lista);
    }

    // 3. Excluir uma estimativa (quando a despesa real acontecer)
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletar(@PathVariable UUID id) {
        if (repository.existsById(id)) {
            repository.deleteById(id);
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.notFound().build();
    }
}

// DTOs auxiliares
record DespesaEstimadaRequestDTO(String descricao, BigDecimal valor, UUID parcelaId) {}
record DespesaEstimadaResponseDTO(UUID id, String descricao, BigDecimal valor) {}