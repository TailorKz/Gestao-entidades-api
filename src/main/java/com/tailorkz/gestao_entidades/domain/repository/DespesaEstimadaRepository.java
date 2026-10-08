package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.DespesaEstimada;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DespesaEstimadaRepository extends JpaRepository<DespesaEstimada, UUID> {

    // Busca todas as estimativas vinculadas a uma parcela específica
    List<DespesaEstimada> findByParcelaId(UUID parcelaId);

    // Ordenação manual (definida pelo gestor) das estimativas de uma parcela.
    List<DespesaEstimada> findByParcelaIdOrderByOrdemAscIdAsc(UUID parcelaId);

    @Query("select coalesce(max(e.ordem), 0) from DespesaEstimada e where e.parcela.id = :parcelaId")
    Integer maiorOrdemDaParcela(@Param("parcelaId") UUID parcelaId);
}