package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.domain.model.Fomento;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/parcelas")
@CrossOrigin(origins = "*")
public class ParcelaController {

    private final ParcelaRepository parcelaRepository;

    public ParcelaController(ParcelaRepository parcelaRepository) {
        this.parcelaRepository = parcelaRepository;
    }

    @GetMapping
    public ResponseEntity<List<ParcelaDTO>> listarTodas() {
        List<ParcelaDTO> lista = parcelaRepository.findAll().stream()
                .map(p -> new ParcelaDTO(
                        p.getId(),
                        p.getFomento().getId(),
                        p.getFomento().getTitulo(),
                        p.getNumero(),
                        p.getValorInicial(),
                        p.getSaldoAtual(),
                        p.getMesesReferencia() // <-- Novo campo enviado para o React
                )).toList();
        return ResponseEntity.ok(lista);
    }

    @PostMapping
    public ResponseEntity<ParcelaDTO> criar(@RequestBody ParcelaRequestDTO dto) {
        Parcela p = new Parcela();

        Fomento f = new Fomento();
        f.setId(dto.fomentoId());
        p.setFomento(f);

        p.setNumero(dto.numero());
        p.setValorInicial(dto.valorInicial());
        p.setSaldoAtual(dto.valorInicial());
        p.setMesesReferencia(dto.mesesReferencia()); // <-- Salva os meses

        Parcela salva = parcelaRepository.save(p);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                new ParcelaDTO(salva.getId(), f.getId(), "Fomento", salva.getNumero(), salva.getValorInicial(), salva.getSaldoAtual(), salva.getMesesReferencia())
        );
    }

    @PutMapping("/{id}")
    public ResponseEntity<ParcelaDTO> atualizar(@PathVariable UUID id, @RequestBody ParcelaUpdateDTO dto) {
        return parcelaRepository.findById(id).map(p -> {
            BigDecimal diferenca = dto.novoValorInicial().subtract(p.getValorInicial());

            p.setValorInicial(dto.novoValorInicial());
            p.setSaldoAtual(p.getSaldoAtual().add(diferenca));
            p.setMesesReferencia(dto.mesesReferencia()); // <-- Atualiza os meses

            Parcela salva = parcelaRepository.save(p);

            return ResponseEntity.ok(new ParcelaDTO(
                    salva.getId(), salva.getFomento().getId(), salva.getFomento().getTitulo(), salva.getNumero(), salva.getValorInicial(), salva.getSaldoAtual(), salva.getMesesReferencia()
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}

// DTOs atualizados
record ParcelaDTO(UUID id, UUID fomentoId, String tituloFomento, Integer numero, BigDecimal valorInicial, BigDecimal saldoAtual, String mesesReferencia) {}
record ParcelaRequestDTO(UUID fomentoId, Integer numero, BigDecimal valorInicial, String mesesReferencia) {}
record ParcelaUpdateDTO(BigDecimal novoValorInicial, String mesesReferencia) {}