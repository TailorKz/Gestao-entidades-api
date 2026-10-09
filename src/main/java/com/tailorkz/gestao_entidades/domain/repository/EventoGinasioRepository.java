package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Ginasio;
import com.tailorkz.gestao_entidades.domain.model.EventoGinasio;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface EventoGinasioRepository extends JpaRepository<EventoGinasio, UUID> {

    List<EventoGinasio> findByTenantIdOrderByDataInicioAsc(UUID tenantId);

    List<EventoGinasio> findByTenantIdAndGinasioOrderByDataInicioAsc(UUID tenantId, Ginasio ginasio);
}
