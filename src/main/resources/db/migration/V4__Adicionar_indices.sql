CREATE INDEX IF NOT EXISTS idx_usuario_tenant_id ON tb_usuario (tenant_id);

CREATE INDEX IF NOT EXISTS idx_fomento_tenant_id ON tb_fomento (tenant_id);

CREATE INDEX IF NOT EXISTS idx_parcela_fomento_id ON tb_parcela (fomento_id);

CREATE INDEX IF NOT EXISTS idx_despesa_parcela_id ON tb_despesa (parcela_id);

CREATE INDEX IF NOT EXISTS idx_despesa_usuario_id ON tb_despesa (usuario_id);

CREATE INDEX IF NOT EXISTS idx_despesa_competencias ON tb_despesa (data_competencia);

CREATE INDEX IF NOT EXISTS idx_documento_anexo_despesa_id ON tb_documento_anexo (despesa_id);

CREATE INDEX IF NOT EXISTS idx_despesa_estimada_parcela_id ON tb_despesa_estimada (parcela_id);

CREATE INDEX IF NOT EXISTS idx_lembrete_tenant_id ON tb_lembrete (tenant_id);

CREATE INDEX IF NOT EXISTS idx_emprestimo_tenant_id ON tb_emprestimo (tenant_id);

CREATE INDEX IF NOT EXISTS idx_reserva_ginasio_tenant_id ON tb_reserva_ginasio (tenant_id);

CREATE INDEX IF NOT EXISTS idx_ajuste_ginasio_tenant_data ON tb_ajuste_ginasio (tenant_id, data);

CREATE INDEX IF NOT EXISTS idx_ajuste_ginasio_reserva_id ON tb_ajuste_ginasio (reserva_id);