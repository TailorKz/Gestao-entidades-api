-- V6__Ordem_despesas.sql
-- Permite ordenar manualmente as despesas e as despesas estimadas dentro de cada parcela.

ALTER TABLE tb_despesa ADD COLUMN IF NOT EXISTS ordem INTEGER;
ALTER TABLE tb_despesa_estimada ADD COLUMN IF NOT EXISTS ordem INTEGER;

-- Backfill: numera as linhas já existentes para que a ordenação parta de uma base estável.
UPDATE tb_despesa d
SET ordem = sub.rn
FROM (
    SELECT id, CAST(ROW_NUMBER() OVER (PARTITION BY parcela_id ORDER BY data_competencia, id) AS INTEGER) AS rn
    FROM tb_despesa
) sub
WHERE d.id = sub.id AND d.ordem IS NULL;

UPDATE tb_despesa_estimada e
SET ordem = sub.rn
FROM (
    SELECT id, CAST(ROW_NUMBER() OVER (PARTITION BY parcela_id ORDER BY id) AS INTEGER) AS rn
    FROM tb_despesa_estimada
) sub
WHERE e.id = sub.id AND e.ordem IS NULL;