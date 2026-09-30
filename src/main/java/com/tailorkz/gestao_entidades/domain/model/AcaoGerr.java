package com.tailorkz.gestao_entidades.domain.model;

import com.tailorkz.gestao_entidades.domain.enums.Categoria;
import jakarta.persistence.*;
import lombok.*;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tb_acao_gerr")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AcaoGerr {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Categoria categoria;

    @Column(nullable = false, length = 200)
    private String nome;

    @Column(nullable = false)
    private Integer posicao;

    @Column(nullable = false)
    private boolean ativo;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "tb_acao_gerr_instrutor",
            joinColumns = @JoinColumn(name = "acao_gerr_id"),
            inverseJoinColumns = @JoinColumn(name = "usuario_id")
    )
    @Builder.Default
    private Set<Usuario> instrutores = new HashSet<>();
}