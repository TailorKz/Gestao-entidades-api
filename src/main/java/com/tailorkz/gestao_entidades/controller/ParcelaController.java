package com.tailorkz.gestao_entidades.controller;

import com.tailorkz.gestao_entidades.controller.dto.ParcelaResponseDTO;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/parcelas")
@CrossOrigin(origins = "*")
public class ParcelaController {

    private final ParcelaRepository parcelaRepository;

    public ParcelaController(ParcelaRepository parcelaRepository) {
        this.parcelaRepository = parcelaRepository;
    }

    @GetMapping
    public ResponseEntity<List<ParcelaResponseDTO>> listarParcelas() {
        List<ParcelaResponseDTO> parcelas = parcelaRepository.findAll().stream()
                .map(p -> new ParcelaResponseDTO(
                        p.getId(),
                        p.getNumero(),
                        p.getValorInicial(),
                        p.getSaldoAtual(),
                        p.getFomento().getTitulo()
                )).toList();

        return ResponseEntity.ok(parcelas);
    }
}