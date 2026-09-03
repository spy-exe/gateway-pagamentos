-- Dados do recebedor Pix ficam no estabelecimento, porque o BR Code precisa da
-- chave, do nome e da cidade de quem recebe.
ALTER TABLE usuario ADD COLUMN chave_pix VARCHAR(80);
ALTER TABLE usuario ADD COLUMN cidade VARCHAR(60);

-- Parcelamento e o copia e cola do Pix pertencem a cobranca.
ALTER TABLE cobranca ADD COLUMN parcelas INTEGER DEFAULT 1 NOT NULL;
ALTER TABLE cobranca ADD COLUMN pix_copia_e_cola VARCHAR(512);

-- O filtro por metodo passou a ser oferecido na listagem.
CREATE INDEX ix_cobranca_usuario_metodo ON cobranca (usuario_id, metodo);
