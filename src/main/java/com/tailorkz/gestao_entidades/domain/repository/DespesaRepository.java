package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.model.Despesa;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface DespesaRepository extends JpaRepository<Despesa, UUID> {
    @EntityGraph(attributePaths = {"usuario", "parcela", "acaoGerr"})
    List<Despesa> findByParcelaId(UUID parcelaId);

    @EntityGraph(attributePaths = {"usuario", "parcela", "acaoGerr"})
    List<Despesa> findByUsuarioId(UUID usuarioId);

    @EntityGraph(attributePaths = {"usuario", "parcela", "acaoGerr"})
    List<Despesa> findByParcelaIdAndUsuarioId(UUID parcelaId, UUID usuarioId);

    // Todas as despesas de um setor (todas as parcelas) — usado no auto-match
    // e no vínculo manual de comprovantes importados por mês.
    @EntityGraph(attributePaths = {"usuario", "parcela", "acaoGerr"})
    List<Despesa> findByParcela_Fomento_TenantIdAndParcela_Fomento_Categoria(UUID tenantId, Categoria categoria);

    // Quantidade de despesas "prontas para envio" agrupada por parcela — UMA query
    // para a extensão GERR (antigamente era 1 chamada por parcela).
    @Query("select d.parcela.id, count(d) from Despesa d " +
            "where d.parcela.fomento.tenant.id = :tenantId and d.status = :status " +
            "group by d.parcela.id")
    List<Object[]> countProntasPorParcela(@Param("tenantId") UUID tenantId, @Param("status") StatusDespesa status);
}