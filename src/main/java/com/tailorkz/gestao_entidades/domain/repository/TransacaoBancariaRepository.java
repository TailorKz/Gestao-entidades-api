package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.TransacaoBancaria;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TransacaoBancariaRepository extends JpaRepository<TransacaoBancaria, UUID> {

    List<TransacaoBancaria> findByConta_IdAndDataBetweenOrderByDataAsc(UUID contaId, LocalDate inicio, LocalDate fim);

    void deleteByConta_IdAndDataBetween(UUID contaId, LocalDate inicio, LocalDate fim);

    void deleteByConta_Id(UUID contaId);

    // Ao excluir um grupo, seus lançamentos ficam "sem grupo" (FK setada para null).
    @Modifying
    @Query("update TransacaoBancaria t set t.grupo = null where t.grupo.id = :grupoId")
    int desvincularDoGrupo(@Param("grupoId") UUID grupoId);
}