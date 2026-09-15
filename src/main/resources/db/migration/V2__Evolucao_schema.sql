-- V2__Evolucao_schema.sql
-- Alinha o schema ao modelo atual de entidades (tb_usuario, tb_fomento, tb_parcela, tb_despesa)
-- e cria as tabelas novas que o V1 não previa.
-- Tudo é idempotente (IF NOT EXISTS / DROP COLUMN IF EXISTS) para ser seguro
-- tanto em bancos já existentes (criados via Hibernate ddl-auto:update) quanto em bases novas.

-- ==============================
-- 1) tb_usuario
-- ==============================
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS login VARCHAR(50);
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS observacoes TEXT;
ALTER TABLE tb_usuario ADD COLUMN IF NOT EXISTS precisa_trocar_senha BOOLEAN DEFAULT TRUE;

UPDATE tb_usuario SET precisa_trocar_senha = TRUE WHERE precisa_trocar_senha IS NULL;

-- A coluna "email" existia apenas no V1 inicial e não faz parte do modelo atual.
-- Removê-la (se ainda existir) evita bloquear a inserção de usuários pelo seed.
ALTER TABLE tb_usuario DROP COLUMN IF EXISTS email;

-- ==============================
-- 2) tb_fomento
-- ==============================
ALTER TABLE tb_fomento ADD COLUMN IF NOT EXISTS categoria VARCHAR(30);

-- ==============================
-- 3) tb_parcela
-- ==============================
ALTER TABLE tb_parcela ADD COLUMN IF NOT EXISTS meses_referencia VARCHAR(255);

-- ==============================
-- 4) tb_despesa
-- ==============================
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS emitente VARCHAR(150);
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS numero_documento VARCHAR(50);
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS data_emissao DATE;
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS descricao TEXT;
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS nome_empresa VARCHAR(200);
ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS observacao TEXT;

-- ==============================
-- 5) tb_despesa_estimada
-- ==============================
CREATE TABLE IF NOT EXISTS tb_despesa_estimada (
    id UUID PRIMARY KEY,
    descricao VARCHAR(255) NOT NULL,
    valor NUMERIC(19, 2) NOT NULL,
    parcela_id UUID NOT NULL,
    CONSTRAINT fk_despesa_estimada_parcela FOREIGN KEY (parcela_id) REFERENCES tb_parcela (id)
);

-- ==============================
-- 6) tb_lembrete
-- ==============================
CREATE TABLE IF NOT EXISTS tb_lembrete (
    id UUID PRIMARY KEY,
    titulo VARCHAR(255) NOT NULL,
    data DATE NOT NULL,
    tenant_id UUID NOT NULL,
    CONSTRAINT fk_lembrete_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

-- ==============================
-- 7) tb_emprestimo
-- ==============================
CREATE TABLE IF NOT EXISTS tb_emprestimo (
    id UUID PRIMARY KEY,
    equipamento VARCHAR(255) NOT NULL,
    nome_retirante VARCHAR(255) NOT NULL,
    data_retirada DATE NOT NULL,
    data_entrega DATE,
    categoria VARCHAR(20) NOT NULL,
    tenant_id UUID NOT NULL,
    CONSTRAINT fk_emprestimo_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);