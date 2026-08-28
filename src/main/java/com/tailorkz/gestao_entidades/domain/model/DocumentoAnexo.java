package com.tailorkz.gestao_entidades.domain.model;

import com.tailorkz.gestao_entidades.domain.enums.TipoDocumento;
import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "tb_documento_anexo")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class DocumentoAnexo {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "despesa_id", nullable = false)
    private Despesa despesa;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TipoDocumento tipo;

    @Column(name = "url_s3", nullable = false, length = 500)
    private String urlS3;

    @Column(name = "chave_s3", nullable = false, length = 250)
    private String chaveS3;

    @Column(name = "status_extracao")
    @Builder.Default
    private Boolean statusExtracao = false;


}