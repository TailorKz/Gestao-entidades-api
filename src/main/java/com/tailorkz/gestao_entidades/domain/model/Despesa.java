package com.tailorkz.gestao_entidades.domain.model;

import com.tailorkz.gestao_entidades.domain.enums.StatusDespesa;
import com.tailorkz.gestao_entidades.domain.enums.TipoDocumentoGerr;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

@Entity
@Table(name = "tb_despesa")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Despesa {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parcela_id", nullable = false)
    private Parcela parcela;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal valor;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private StatusDespesa status;

    @Column(name = "data_competencia", nullable = false)
    private YearMonth dataCompetencia;

    @Column(length = 150)
    private String emitente;

    @Column(name = "numero_documento", length = 50)
    private String numeroDocumento;

    @Column(name = "data_emissao")
    private LocalDate dataEmissao;

    @Column(columnDefinition = "TEXT")
    private String descricao;

    @Column(name = "nome_empresa", length = 200)
    private String nomeEmpresa;

    @Column(name = "documento_favorecido", length = 30)
    private String documentoFavorecido;

    @Column(columnDefinition = "TEXT")
    private String observacao;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_documento_gerr", length = 30)
    private TipoDocumentoGerr tipoDocumentoGerr;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "acao_gerr_id")
    private AcaoGerr acaoGerr;
}