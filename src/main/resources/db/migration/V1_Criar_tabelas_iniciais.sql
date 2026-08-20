-- V1__Criar_tabelas_iniciais.sql

CREATE TABLE tb_tenant (
    id UUID PRIMARY KEY,
    nome VARCHAR(150) NOT NULL,
    cnpj VARCHAR(18) NOT NULL UNIQUE
);

CREATE TABLE tb_usuario (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    nome VARCHAR(150) NOT NULL,
    email VARCHAR(150) NOT NULL UNIQUE,
    senha_hash VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    categoria VARCHAR(30),
    CONSTRAINT fk_usuario_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

CREATE TABLE tb_fomento (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    titulo VARCHAR(200) NOT NULL,
    valor_total DECIMAL(15, 2) NOT NULL,
    ano_vigencia INTEGER NOT NULL,
    CONSTRAINT fk_fomento_tenant FOREIGN KEY (tenant_id) REFERENCES tb_tenant (id)
);

CREATE TABLE tb_parcela (
    id UUID PRIMARY KEY,
    fomento_id UUID NOT NULL,
    numero INTEGER NOT NULL,
    valor_inicial DECIMAL(15, 2) NOT NULL,
    saldo_atual DECIMAL(15, 2) NOT NULL,
    CONSTRAINT fk_parcela_fomento FOREIGN KEY (fomento_id) REFERENCES tb_fomento (id)
);

CREATE TABLE tb_despesa (
    id UUID PRIMARY KEY,
    parcela_id UUID NOT NULL,
    usuario_id UUID NOT NULL,
    valor DECIMAL(15, 2) NOT NULL,
    status VARCHAR(30) NOT NULL,
    data_competencia DATE NOT NULL,
    CONSTRAINT fk_despesa_parcela FOREIGN KEY (parcela_id) REFERENCES tb_parcela (id),
    CONSTRAINT fk_despesa_usuario FOREIGN KEY (usuario_id) REFERENCES tb_usuario (id)
);

CREATE TABLE tb_documento_anexo (
    id UUID PRIMARY KEY,
    despesa_id UUID NOT NULL,
    tipo VARCHAR(30) NOT NULL,
    url_s3 VARCHAR(500) NOT NULL,
    chave_s3 VARCHAR(250) NOT NULL,
    status_extracao BOOLEAN DEFAULT FALSE,
    CONSTRAINT fk_documento_despesa FOREIGN KEY (despesa_id) REFERENCES tb_despesa (id)
);