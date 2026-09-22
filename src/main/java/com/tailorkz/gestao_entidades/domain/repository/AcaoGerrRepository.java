package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.AcaoGerr;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface AcaoGerrRepository extends JpaRepository<AcaoGerr, UUID> {

    List<AcaoGerr> findByCategoriaOrderByPosicaoAsc(Categoria categoria);

    List<AcaoGerr> findByCategoriaAndAtivoTrueOrderByPosicaoAsc(Categoria categoria);

    @Query("select coalesce(max(a.posicao), 0) from AcaoGerr a where a.categoria = :categoria")
    Integer maxPosicaoDaCategoria(@Param("categoria") Categoria categoria);
}