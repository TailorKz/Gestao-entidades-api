package com.tailorkz.gestao_entidades.domain.model;

import com.tailorkz.gestao_entidades.domain.enums.DiaSemana;
import com.tailorkz.gestao_entidades.domain.enums.Ginasio;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "tb_reserva_ginasio")
public class ReservaGinasio {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private Ginasio ginasio;

    @Column(nullable = false, length = 150)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "dia_semana", nullable = false, length = 20)
    private DiaSemana diaSemana;

    @Column(name = "valor_dia", precision = 10, scale = 2)
    private BigDecimal valorDia = new BigDecimal("25.00");

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public Tenant getTenant() { return tenant; }
    public void setTenant(Tenant tenant) { this.tenant = tenant; }

    public Ginasio getGinasio() { return ginasio; }
    public void setGinasio(Ginasio ginasio) { this.ginasio = ginasio; }

    public String getNome() { return nome; }
    public void setNome(String nome) { this.nome = nome; }

    public DiaSemana getDiaSemana() { return diaSemana; }
    public void setDiaSemana(DiaSemana diaSemana) { this.diaSemana = diaSemana; }

    public BigDecimal getValorDia() { return valorDia; }
    public void setValorDia(BigDecimal valorDia) { this.valorDia = valorDia; }
}