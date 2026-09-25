package com.tailorkz.gestao_entidades.domain.model;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "tb_comprovante_bb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ComprovanteBb {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    // Setor (ESPORTE/CULTURA) informado na importação por mês
    @Enumerated(EnumType.STRING)
    @Column(length = 30)
    private Categoria categoria;

    // Mês "balde" de organização — o comprovante nasce sem parcela e é
    // alocado na parcela da despesa apenas quando é vinculado.
    @Column(name = "data_referencia")
    private java.time.LocalDate dataReferencia;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcela_id")
    private Parcela parcela;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Column(name = "data_pagamento")
    private LocalDate dataPagamento;

    @Column(length = 250)
    private String favorecido;

    @Column(name = "documento_favorecido", length = 30)
    private String documentoFavorecido;

    @Column(length = 150)
    private String autenticacao;

    @Column(name = "nome_arquivo", length = 250)
    private String nomeArquivo;

    @Column(name = "hash_arquivo", nullable = false, length = 64)
    private String hashArquivo;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despesa_id")
    private Despesa despesa;

    @Column(nullable = false)
    private Boolean vinculado = false;

    @JdbcTypeCode(SqlTypes.BINARY)
    @Column(name = "arquivo_pdf", columnDefinition = "BYTEA")
    private byte[] arquivoPdf;

    @Column(name = "chave_s3", length = 250)
    private String chaveS3;
}