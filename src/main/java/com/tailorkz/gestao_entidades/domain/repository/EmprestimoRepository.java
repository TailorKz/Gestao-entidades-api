package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.Emprestimo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface EmprestimoRepository extends JpaRepository<Emprestimo, UUID> {

    List<Emprestimo> findByTenantIdAndCategoriaOrderByDataRetiradaDesc(UUID tenantId, String categoria);

    long countByTenantIdAndCategoriaAndDataEntregaIsNull(UUID tenantId, String categoria);

    List<Emprestimo> findByTenantIdAndCategoriaAndDataEntregaIsNullOrderByDataRetiradaDesc(UUID tenantId, String categoria);

    List<Emprestimo> findByTenantIdAndCategoriaAndDataEntregaIsNotNullOrderByDataEntregaDesc(UUID tenantId, String categoria);

    List<Emprestimo> findByTenantIdAndCategoriaAndDataEntregaBefore(UUID tenantId, String categoria, LocalDate data);
}