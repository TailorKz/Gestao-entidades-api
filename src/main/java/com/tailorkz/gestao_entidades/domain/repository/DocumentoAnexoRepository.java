package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import com.tailorkz.gestao_entidades.domain.model.DocumentoAnexo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DocumentoAnexoRepository extends JpaRepository<DocumentoAnexo, UUID> {

    List<DocumentoAnexo> findByDespesaId(UUID despesaId);

    List<DocumentoAnexo> findByDespesaIdAndTipo(UUID despesaId, TipoDocumento tipo);

    List<DocumentoAnexo> findByUrlS3(String urlS3);

    boolean existsByDespesaIdAndTipo(UUID despesaId, TipoDocumento tipo);

    @Query("select distinct a.despesa.id, a.tipo from DocumentoAnexo a where a.despesa.id in :despesaIds")
    List<Object[]> findTiposPorDespesas(@Param("despesaIds") Collection<UUID> despesaIds);

    // Projeção sem carregar a entidade Despesa (evita N+1 e lazy load).
    @Query("select a.despesa.id, a.tipo, a.chaveS3, a.urlS3 from DocumentoAnexo a where a.despesa.id in :despesaIds")
    List<Object[]> findAnexosComDespesaId(@Param("despesaIds") Collection<UUID> despesaIds);
}