-- V3__Criar_ginasios.sql
-- Tabelas do Controle de Ginásios (reservas fixas semanais + ajustes de dias).
-- DDL idempotente: seguro para bases existentes e para bases novas.

CREATE TABLE IF NOT EXISTS tb_reserva_ginasio (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ginasio VARCHAR(30) NOT NULL,
    nome VARCHAR(150) NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    valor_dia NUMERIC(10, 2) DEFAULT 25.00,
    CONSTRAINT fk_reserva_ginasio_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

ALTER TABLE tb_reserva_ginasio ADD COLUMN IF NOT EXISTS valor_dia NUMERIC(10, 2) DEFAULT 25.00;

CREATE TABLE IF NOT EXISTS tb_ajuste_ginasio (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ginasio VARCHAR(30) NOT NULL,
    data DATE NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    reserva_id UUID,
    motivo VARCHAR(255),
    CONSTRAINT fk_ajuste_ginasio_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id),
    CONSTRAINT fk_ajuste_ginasio_reserva FOREIGN KEY (reserva_id) REFERENCES tb_reserva_ginasio (id)
);