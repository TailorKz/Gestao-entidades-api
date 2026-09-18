package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComprovanteBbRepository extends JpaRepository<ComprovanteBb, UUID> {

    Optional<ComprovanteBb> findByHashArquivo(String hashArquivo);

    @EntityGraph(attributePaths = "despesa")
    List<ComprovanteBb> findByParcelaId(UUID parcelaId);

    @EntityGraph(attributePaths = "despesa")
    List<ComprovanteBb> findByParcelaIdAndDespesaIsNull(UUID parcelaId);

    @EntityGraph(attributePaths = "despesa")
    List<ComprovanteBb> findByParcelaIdAndDespesaIsNotNull(UUID parcelaId);

    @EntityGraph(attributePaths = "despesa")
    List<ComprovanteBb> findByDespesaId(UUID despesaId);
}