package com.tailorkz.gestao_entidades.domain.model;

import jakarta.persistence.*;
import lombok.*;
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
    @JoinColumn(name = "parcela_id", nullable = false)
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

    @Lob
    @Column(name = "arquivo_pdf", columnDefinition = "BYTEA")
    private byte[] arquivoPdf;
}