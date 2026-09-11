package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.Parcela;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ParcelaRepository extends JpaRepository<Parcela, UUID> {

    List<Parcela> findByFomento_TenantId(UUID tenantId);

    List<Parcela> findByFomento_TenantIdAndFomento_Categoria(UUID tenantId, Categoria categoria);

    List<Parcela> findAllByFomento_Categoria(Categoria categoria);
}