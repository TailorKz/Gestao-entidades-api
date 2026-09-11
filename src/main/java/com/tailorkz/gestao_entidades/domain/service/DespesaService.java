package com.tailorkz.gestao_entidades.domain.service;

import com.tailorkz.gestao_entidades.domain.model.Despesa;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import com.tailorkz.gestao_entidades.domain.repository.DespesaRepository;
import com.tailorkz.gestao_entidades.domain.repository.ParcelaRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

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
        if (novaDespesa.getValor() == null || novaDespesa.getValor().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O valor da despesa deve ser maior que zero.");
        }

        Parcela parcela = parcelaRepository.findById(novaDespesa.getParcela().getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcela não encontrada."));

        if (novaDespesa.getValor().compareTo(parcela.getSaldoAtual()) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Valor da despesa excede o saldo disponível da parcela (R$ " + parcela.getSaldoAtual() + ").");
        }

        Despesa despesaSalva = despesaRepository.save(novaDespesa);

        parcela.setSaldoAtual(parcela.getSaldoAtual().subtract(despesaSalva.getValor()));
        parcelaRepository.save(parcela);

        return despesaSalva;
    }

    public Parcela buscarParcela(UUID parcelaId) {
        return parcelaRepository.findById(parcelaId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parcela não encontrada."));
    }

    public Parcela parcelaDoTenant(UUID parcelaId, UUID tenantId) {
        Parcela parcela = buscarParcela(parcelaId);
        if (!parcela.getFomento().getTenant().getId().equals(tenantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acesso negado a outra entidade.");
        }
        return parcela;
    }
}