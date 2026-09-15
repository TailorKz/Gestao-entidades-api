package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.AjusteGinasio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AjusteGinasioRepository extends JpaRepository<AjusteGinasio, UUID> {

    List<AjusteGinasio> findByTenantId(UUID tenantId);

    List<AjusteGinasio> findByTenantIdAndDataBetween(UUID tenantId, LocalDate inicio, LocalDate fim);

    void deleteByReservaId(UUID reservaId);
}