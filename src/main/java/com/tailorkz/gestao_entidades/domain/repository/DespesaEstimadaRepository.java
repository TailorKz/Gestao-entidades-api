package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.DespesaEstimada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DespesaEstimadaRepository extends JpaRepository<DespesaEstimada, UUID> {

    // Busca todas as estimativas vinculadas a uma parcela específica
    List<DespesaEstimada> findByParcelaId(UUID parcelaId);
}