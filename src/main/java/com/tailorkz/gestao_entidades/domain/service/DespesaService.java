package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DespesaService {

    private final DespesaRepository despesaRepository;
    private final ParcelaRepository parcelaRepository;

    public DespesaService(DespesaRepository despesaRepository, ParcelaRepository parcelaRepository) {
        this.despesaRepository = despesaRepository;
        this.parcelaRepository = parcelaRepository;
    }

    @Transactional
    public Despesa registrarNovaDespesa(Despesa novaDespesa) {
        Despesa despesaSalva = despesaRepository.save(novaDespesa);

        Parcela parcela = parcelaRepository.findById(novaDespesa.getParcela().getId())
                .orElseThrow(() -> new RuntimeException("Parcela não encontrada!"));

        parcela.setSaldoAtual(parcela.getSaldoAtual().subtract(despesaSalva.getValor()));

        parcelaRepository.save(parcela);

        return despesaSalva;
    }
}