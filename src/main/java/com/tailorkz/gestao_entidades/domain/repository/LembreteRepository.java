package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.Lembrete;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface LembreteRepository extends JpaRepository<Lembrete, UUID> {

    List<Lembrete> findByTenantIdAndDataOrderByDataAsc(UUID tenantId, LocalDate data);

    List<Lembrete> findByTenantIdAndDataGreaterThanEqualOrderByDataAsc(UUID tenantId, LocalDate data);

    List<Lembrete> findByTenantIdAndDataBetweenOrderByDataAsc(UUID tenantId, LocalDate inicio, LocalDate fim);
}