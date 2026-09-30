package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.GrupoPrestacao;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface GrupoPrestacaoRepository extends JpaRepository<GrupoPrestacao, UUID> {

    List<GrupoPrestacao> findByConta_IdOrderByOrdemAsc(UUID contaId);
}