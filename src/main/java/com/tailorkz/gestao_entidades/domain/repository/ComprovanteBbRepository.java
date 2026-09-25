package com.tailorkz.gestao_entidades.domain.repository;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import com.tailorkz.gestao_entidades.domain.model.ComprovanteBb;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ComprovanteBbRepository extends JpaRepository<ComprovanteBb, UUID> {

    Optional<ComprovanteBb> findByHashArquivo(String hashArquivo);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByParcelaId(UUID parcelaId);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByParcelaIdOrderByDataPagamentoDesc(UUID parcelaId);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByParcelaIdAndDespesaIsNull(UUID parcelaId);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByParcelaIdAndDespesaIsNotNull(UUID parcelaId);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByDespesaId(UUID despesaId);

    // --- Consultas do modelo por mês / setor ---

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByTenant_IdAndCategoriaAndDataReferenciaOrderByDataPagamentoDesc(
            UUID tenantId, Categoria categoria, LocalDate dataReferencia);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByTenant_IdAndCategoriaAndDataReferenciaBetweenOrderByDataPagamentoDesc(
            UUID tenantId, Categoria categoria, LocalDate inicio, LocalDate fim);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByTenant_IdAndCategoria(UUID tenantId, Categoria categoria);

    @EntityGraph(attributePaths = {"despesa", "despesa.parcela"})
    List<ComprovanteBb> findByTenant_IdAndCategoriaAndDespesaIsNull(UUID tenantId, Categoria categoria);

    // Projeção para a extensão GERR: data de pagamento de todos os comprovantes
    // das despesas do lote em UMA query (sem N+1 e sem lazy load).
    @Query("select c.despesa.id, c.dataPagamento from ComprovanteBb c " +
            "where c.despesa.id in :despesaIds and c.dataPagamento is not null")
    List<Object[]> findDataPagamentoPorDespesa(@Param("despesaIds") Collection<UUID> despesaIds);
}