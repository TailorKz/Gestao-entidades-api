package com.tailorkz.gestao_entidades.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "tb_grupo_prestacao")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class GrupoPrestacao {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    // Grupos são por conta bancária ("separados por banco").
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conta_bancaria_id", nullable = false)
    private ContaBancaria conta;

    @Column(nullable = false, length = 60)
    private String nome;

    // Cor hex opcional para os gráficos (ex: #F59E0B).
    @Column(length = 20)
    private String cor;

    @Column(nullable = false)
    private Integer ordem = 0;
}