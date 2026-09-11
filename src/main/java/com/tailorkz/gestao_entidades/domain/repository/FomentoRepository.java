package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.Fomento;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FomentoRepository extends JpaRepository<Fomento, UUID> {

    List<Fomento> findAllByTenantId(UUID tenantId);

    Optional<Fomento> findFirstByTenantId(UUID tenantId);

    List<Fomento> findAllByTenantIdAndCategoria(UUID tenantId, Categoria categoria);

    Optional<Fomento> findFirstByTenantIdAndCategoria(UUID tenantId, Categoria categoria);

    List<Fomento> findAllByCategoria(Categoria categoria);
}