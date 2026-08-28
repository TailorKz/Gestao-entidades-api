package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoAnexoRepository extends JpaRepository<DocumentoAnexo, UUID> {

    List<DocumentoAnexo> findByDespesaId(UUID despesaId);
}