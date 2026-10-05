package com.tailorkz.gestao_entidades.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "tb_checklist_pagamento",
        uniqueConstraints = @UniqueConstraint(columnNames = {"tenant_id", "usuario_id", "ano_referencia", "mes_referencia"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class ChecklistPagamento {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(name = "ano_referencia", nullable = false)
    private Integer ano;

    @Column(name = "mes_referencia", nullable = false)
    private Integer mes;

    @Column(name = "entregou_documentos", nullable = false)
    @Builder.Default
    private Boolean entregouDocumentos = false;

    @Column(name = "pagamento_feito", nullable = false)
    @Builder.Default
    private Boolean pagamentoFeito = false;

    @Column(name = "atualizado_em")
    private LocalDateTime atualizadoEm;

    @PrePersist
    @PreUpdate
    void tocarAtualizacao() {
        this.atualizadoEm = LocalDateTime.now();
    }
}
