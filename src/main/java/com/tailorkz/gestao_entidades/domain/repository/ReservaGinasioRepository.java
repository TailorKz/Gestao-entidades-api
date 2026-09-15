package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.ReservaGinasio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ReservaGinasioRepository extends JpaRepository<ReservaGinasio, UUID> {

    List<ReservaGinasio> findByTenantIdOrderByNomeAsc(UUID tenantId);
}