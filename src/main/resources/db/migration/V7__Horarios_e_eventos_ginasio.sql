-- V7__Horarios_e_eventos_ginasio.sql
-- Grade de horários (recorrente semanal) e eventos (reservas por data) dos ginásios.
-- DDL idempotente: seguro para bases existentes e para bases novas.

CREATE TABLE IF NOT EXISTS tb_horario_ginasio (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ginasio VARCHAR(30) NOT NULL,
    dia_semana VARCHAR(20) NOT NULL,
    hora_inicio TIME NOT NULL,
    hora_fim TIME NOT NULL,
    nome VARCHAR(150) NOT NULL,
    observacao VARCHAR(255),
    criado_em TIMESTAMP,
    CONSTRAINT fk_horario_ginasio_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

CREATE INDEX IF NOT EXISTS idx_horario_ginasio_tenant ON tb_horario_ginasio (tenant_id, ginasio);

CREATE TABLE IF NOT EXISTS tb_evento_ginasio (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    ginasio VARCHAR(30) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    descricao VARCHAR(255),
    data_inicio DATE NOT NULL,
    data_fim DATE NOT NULL,
    hora_inicio TIME,
    hora_fim TIME,
    criado_em TIMESTAMP,
    CONSTRAINT fk_evento_ginasio_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

CREATE INDEX IF NOT EXISTS idx_evento_ginasio_tenant ON tb_evento_ginasio (tenant_id, ginasio);
