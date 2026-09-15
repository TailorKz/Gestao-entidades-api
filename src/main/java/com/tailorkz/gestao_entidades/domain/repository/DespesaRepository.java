package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.Despesa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, UUID> {
    @EntityGraph(attributePaths = "usuario")
    List<Despesa> findByParcelaId(UUID parcelaId);

    @EntityGraph(attributePaths = "usuario")
    List<Despesa> findByUsuarioId(UUID usuarioId);

    @EntityGraph(attributePaths = "usuario")
    List<Despesa> findByParcelaIdAndUsuarioId(UUID parcelaId, UUID usuarioId);
}