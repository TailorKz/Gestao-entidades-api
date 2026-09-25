-- V5__Comprovantes_por_mes.sql
-- Redesenho da conciliação bancária: o comprovante passa a ser importado por
-- mês/setor e nasce SEM parcela (alocada apenas no vínculo com a despesa).
-- O arquivo tb_comprovante_bb não tinha migração (era mantido pelo ddl-auto);
-- por isso o bloco abaixo garante o schema completo mesmo em bases novas.

CREATE TABLE IF NOT EXISTS tb_comprovante_bb (
    id UUID PRIMARY KEY,
    tenant_id UUID,
    categoria VARCHAR(30),
    data_referencia DATE,
    parcela_id UUID,
    valor DECIMAL(15, 2) NOT NULL,
    data_pagamento DATE,
    favorecido VARCHAR(250),
    documento_favorecido VARCHAR(30),
    autenticacao VARCHAR(150),
    nome_arquivo VARCHAR(250),
    hash_arquivo VARCHAR(64) NOT NULL,
    despesa_id UUID,
    vinculado BOOLEAN NOT NULL DEFAULT FALSE,
    arquivo_pdf BYTEA,
    chave_s3 VARCHAR(250),
    CONSTRAINT fk_comprovante_bb_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id),
    CONSTRAINT fk_comprovante_bb_parcela FOREIGN KEY (parcela_id) REFERENCES tb_parcela (id),
    CONSTRAINT fk_comprovante_bb_despesa FOREIGN KEY (despesa_id) REFERENCES tb_despesa (id)
);

-- Bases antigas: parcela era obrigatória
ALTER TABLE tb_comprovante_bb ALTER COLUMN parcela_id DROP NOT NULL;

-- Bases antigas: colunas novas (criadas a partir de agora no próprio índice)
ALTER TABLE tb_comprovante_bb ADD COLUMN IF NOT EXISTS tenant_id UUID;
ALTER TABLE tb_comprovante_bb ADD COLUMN IF NOT EXISTS categoria VARCHAR(30);
ALTER TABLE tb_comprovante_bb ADD COLUMN IF NOT EXISTS data_referencia DATE;

CREATE INDEX IF NOT EXISTS idx_comprovante_mes
    ON tb_comprovante_bb (tenant_id, categoria, data_referencia);

CREATE INDEX IF NOT EXISTS idx_comprovante_despesa_id ON tb_comprovante_bb (despesa_id);