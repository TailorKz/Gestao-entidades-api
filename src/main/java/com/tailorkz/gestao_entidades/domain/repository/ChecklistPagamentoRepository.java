package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.ChecklistPagamento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ChecklistPagamentoRepository extends JpaRepository<ChecklistPagamento, UUID> {

    List<ChecklistPagamento> findByTenantIdAndAnoAndMes(UUID tenantId, Integer ano, Integer mes);

    Optional<ChecklistPagamento> findByTenantIdAndUsuario_IdAndAnoAndMes(UUID tenantId, UUID usuarioId, Integer ano, Integer mes);
}
