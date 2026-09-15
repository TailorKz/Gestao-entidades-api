package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {

    @EntityGraph(attributePaths = "fomento")
    List<Parcela> findByFomento_TenantId(UUID tenantId);

    @EntityGraph(attributePaths = "fomento")
    List<Parcela> findByFomento_TenantIdAndFomento_Categoria(UUID tenantId, Categoria categoria);

    @EntityGraph(attributePaths = "fomento")
    List<Parcela> findAllByFomento_Categoria(Categoria categoria);

    @Override
    @EntityGraph(attributePaths = "fomento")
    List<Parcela> findAll();
}