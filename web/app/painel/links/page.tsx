"use client";

import { useCallback, useEffect, useState } from "react";
import LinhaConta from "@/components/LinhaConta";
import { api, ErroDaApi, type LinkPagamento, type MetodoPagamento } from "@/lib/api";
import { dataHora, moeda, rotuloMetodo } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

const ROTULOS_DA_SITUACAO: Record<LinkPagamento["situacao"], string> = {
  ATIVO: "Aceitando pagamentos",
  PAUSADO: "Pausado",
  EXPIRADO: "Expirado",
  ESGOTADO: "Limite atingido"
};

export default function LinksDePagamento() {
  const [links, setLinks] = useState<LinkPagamento[]>([]);
  const [descricao, setDescricao] = useState("");
  const [centavos, setCentavos] = useState("");
  const [metodo, setMetodo] = useState<MetodoPagamento>("PIX");
  const [parcelasMaximas, setParcelasMaximas] = useState(1);
  const [limiteDeUsos, setLimiteDeUsos] = useState("");
  const [expiraEm, setExpiraEm] = useState("");
  const [copiado, setCopiado] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);

  const carregar = useCallback(async () => {
    const sessao = lerSessao();
    if (!sessao) return;

    try {
      setLinks(await api.listarLinks(sessao.token));
      setErro(null);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel carregar os links.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function criar(evento: React.FormEvent) {
    evento.preventDefault();
    const sessao = lerSessao();
    if (!sessao) return;

    setEnviando(true);
    setErro(null);
    setCampos({});

    try {
      const criado = await api.criarLink(sessao.token, {
        descricao,
        valorEmCentavos: Number(centavos),
        metodo,
        parcelasMaximas: metodo === "CARTAO_CREDITO" ? parcelasMaximas : 1,
        limiteDeUsos: limiteDeUsos ? Number(limiteDeUsos) : undefined,
        expiraEm: expiraEm ? new Date(expiraEm).toISOString() : undefined
      });

      setDescricao("");
      setCentavos("");
      setMetodo("PIX");
      setParcelasMaximas(1);
      setLimiteDeUsos("");
      setExpiraEm("");
      setLinks((atuais) => [criado, ...atuais]);
      await copiarLink(criado.codigo);
    } catch (falha) {
      if (falha instanceof ErroDaApi) {
        setErro(falha.message);
        setCampos(falha.campos);
      } else {
        setErro("Nao foi possivel criar o link.");
      }
    } finally {
      setEnviando(false);
    }
  }

  async function copiarLink(codigo: string) {
    const endereco = `${window.location.origin}/pagar/${codigo}`;
    try {
      await navigator.clipboard.writeText(endereco);
      setCopiado(codigo);
      window.setTimeout(() => setCopiado((atual) => (atual === codigo ? null : atual)), 2200);
    } catch {
      setCopiado(null);
    }
  }

  async function alternar(link: LinkPagamento) {
    const sessao = lerSessao();
    if (!sessao) return;

    try {
      const atualizado = await api.alternarLink(sessao.token, link.codigo, !link.ativo);
      setLinks((atuais) => atuais.map((item) => (item.codigo === atualizado.codigo ? atualizado : item)));
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel alterar o link.");
    }
  }

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">Canais de venda</span>
          <h2>Links de pagamento</h2>
        </div>
        <p className="cabecalho-apoio">Venda por mensagem, rede social ou QR sem construir outro checkout.</p>
      </div>

      <section className="links-grade">
        <form className="formulario links-formulario" onSubmit={criar}>
          <div>
            <span className="etiqueta">Novo link</span>
            <h3>Prepare a cobranca</h3>
          </div>

          <label className="campo">
            <span>O que esta sendo vendido</span>
            <input
              value={descricao}
              onChange={(evento) => setDescricao(evento.target.value)}
              placeholder="Cesta especial da casa"
              maxLength={140}
              required
            />
            {campos.descricao && <em className="campo-erro">{campos.descricao}</em>}
          </label>

          <div className="dupla">
            <label className="campo" data-mono="sim">
              <span>Valor</span>
              <input
                inputMode="numeric"
                value={centavos ? moeda(Number(centavos)) : ""}
                onChange={(evento) => setCentavos(evento.target.value.replace(/\D/g, "").slice(0, 9))}
                placeholder="R$ 0,00"
                required
              />
              {campos.valorEmCentavos && <em className="campo-erro">{campos.valorEmCentavos}</em>}
            </label>

            <label className="campo">
              <span>Metodo</span>
              <select value={metodo} onChange={(evento) => setMetodo(evento.target.value as MetodoPagamento)}>
                <option value="PIX">Pix</option>
                <option value="CARTAO_CREDITO">Cartao de credito</option>
                <option value="CARTAO_DEBITO">Cartao de debito</option>
              </select>
            </label>
          </div>

          {metodo === "CARTAO_CREDITO" && (
            <label className="campo">
              <span>Parcelamento maximo</span>
              <select value={parcelasMaximas} onChange={(evento) => setParcelasMaximas(Number(evento.target.value))}>
                {Array.from({ length: 12 }, (_, indice) => indice + 1).map((quantidade) => (
                  <option key={quantidade} value={quantidade}>
                    {quantidade === 1 ? "Somente a vista" : `Ate ${quantidade} vezes`}
                  </option>
                ))}
              </select>
            </label>
          )}

          <div className="dupla">
            <label className="campo">
              <span>Limite de pagamentos</span>
              <input
                type="number"
                min="1"
                value={limiteDeUsos}
                onChange={(evento) => setLimiteDeUsos(evento.target.value)}
                placeholder="Sem limite"
              />
            </label>
            <label className="campo">
              <span>Valido ate</span>
              <input
                type="datetime-local"
                value={expiraEm}
                onChange={(evento) => setExpiraEm(evento.target.value)}
              />
            </label>
          </div>

          {erro && <p className="aviso">{erro}</p>}

          <button className="botao" type="submit" disabled={enviando || !centavos}>
            {enviando ? "Preparando" : "Criar e copiar link"}
          </button>
        </form>

        <aside className="links-explicacao">
          <span className="etiqueta">Da vitrine ao razao</span>
          <h3>Um endereco pronto para receber</h3>
          <p>
            Quem abre o link ve apenas a oferta, o estabelecimento e os campos necessarios para pagar.
            A cobranca entra no mesmo fluxo de autorizacao, aparece no painel e dispara os mesmos webhooks.
          </p>
          <LinhaConta rotulo="Uso" valor="WhatsApp, bio, e-mail" />
          <LinhaConta rotulo="Controle" valor="pausa, validade, estoque" />
          <LinhaConta rotulo="Rastreio" valor="origem gravada na cobranca" />
        </aside>
      </section>

      <div className="subcabecalho">
        <div>
          <span className="etiqueta">Catalogo</span>
          <h3>{links.length === 1 ? "1 link criado" : `${links.length} links criados`}</h3>
        </div>
      </div>

      {carregando ? (
        <p className="carregando">Carregando</p>
      ) : links.length === 0 ? (
        <div className="vazio">
          <p>Crie o primeiro link para transformar qualquer conversa em um ponto de venda.</p>
        </div>
      ) : (
        <div className="links-lista">
          {links.map((link) => (
            <article className="link-cartao" key={link.codigo}>
              <div className="link-cartao-principal">
                <span className="selo" data-estado={link.situacao === "ATIVO" ? "CAPTURADA" : "CANCELADA"}>
                  {ROTULOS_DA_SITUACAO[link.situacao]}
                </span>
                <h3>{link.descricao}</h3>
                <p className="link-endereco">/pagar/{link.codigo}</p>
              </div>

              <div className="link-cartao-numeros">
                <strong>{moeda(link.valorEmCentavos)}</strong>
                <span>{rotuloMetodo(link.metodo)}{link.parcelasMaximas > 1 ? ` · ate ${link.parcelasMaximas}x` : ""}</span>
              </div>

              <div className="link-cartao-numeros">
                <strong>{link.usos}{link.limiteDeUsos ? ` / ${link.limiteDeUsos}` : ""}</strong>
                <span>pagamentos{link.expiraEm ? ` · ate ${dataHora(link.expiraEm)}` : ""}</span>
              </div>

              <div className="acoes-botoes">
                <button className="botao" data-tom="vazado" type="button" onClick={() => copiarLink(link.codigo)}>
                  {copiado === link.codigo ? "Copiado" : "Copiar"}
                </button>
                <a className="botao" data-tom="vazado" href={`/pagar/${link.codigo}`} target="_blank" rel="noreferrer">
                  Abrir
                </a>
                <button className="botao" data-tom="vazado" type="button" onClick={() => alternar(link)}>
                  {link.ativo ? "Pausar" : "Reativar"}
                </button>
              </div>
            </article>
          ))}
        </div>
      )}

      <p className="sr-status" aria-live="polite">
        {copiado ? "Link copiado para a area de transferencia." : ""}
      </p>
    </>
  );
}
