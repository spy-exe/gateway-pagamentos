-- As cobrancas da conta de demonstracao nascem todas no mesmo minuto, o que
-- deixa o extrato com cara de dado sintetico. Este script empurra cada uma
-- para um ponto dos ultimos quarenta e cinco dias, deslocando junto os eventos
-- e os estornos, de modo que a ordem interna de cada transacao continue certa.
--
-- Uso: psql -d gateway -f espalhar-datas.sql

BEGIN;

CREATE TEMP TABLE deslocamento ON COMMIT DROP AS
SELECT c.id, ((random() * 44 + 0.4) * interval '1 day') AS atraso
FROM cobranca c
JOIN usuario u ON u.id = c.usuario_id
WHERE u.email = 'demo@aval.app';

UPDATE cobranca c
SET criado_em = c.criado_em - d.atraso,
    atualizado_em = c.atualizado_em - d.atraso
FROM deslocamento d
WHERE d.id = c.id;

UPDATE evento_cobranca e
SET criado_em = e.criado_em - d.atraso
FROM deslocamento d
WHERE d.id = e.cobranca_id;

UPDATE estorno s
SET criado_em = s.criado_em - d.atraso
FROM deslocamento d
WHERE d.id = s.cobranca_id;

COMMIT;
