"use client";

import { useCallback, useEffect, useState } from "react";
import LinhaConta from "@/components/LinhaConta";
import {
  api,
  ErroDaApi,
  type EndpointWebhook,
  type EntregaWebhook
} from "@/lib/api";
import { dataHora } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

export default function Webhooks() {
  const [endpoints, setEndpoints] = useState<EndpointWebhook[]>([]);
  const [entregas, setEntregas] = useState<Record<string, EntregaWebhook[]>>({});
  const [aberto, setAberto] = useState<string | null>(null);
  const [url, setUrl] = useState("");
  const [descricao, setDescricao] = useState("");
  const [segredoNovo, setSegredoNovo] = useState<string | null>(null);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);

  const carregar = useCallback(async () => {
    const sessao = lerSessao();
    if (!sessao) return;

    try {
      setEndpoints(await api.listarWebhooks(sessao.token));
      setErro(null);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel carregar os endpoints.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function cadastrar(evento: React.FormEvent) {
    evento.preventDefault();
    const sessao = lerSessao();
    if (!sessao) return;

    setEnviando(true);
    setErro(null);

    try {
      const criado = await api.criarWebhook(sessao.token, { url, descricao: descricao || undefined });
      setSegredoNovo(criado.segredo);
      setUrl("");
      setDescricao("");
      await carregar();
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel cadastrar o endpoint.");
    } finally {
      setEnviando(false);
    }
  }

  async function abrirEntregas(codigo: string) {
    const sessao = lerSessao();
    if (!sessao) return;

    if (aberto === codigo) {
      setAberto(null);
      return;
    }

    setAberto(codigo);
    const pagina = await api.entregasDoWebhook(sessao.token, codigo);
    setEntregas((atual) => ({ ...atual, [codigo]: pagina.itens }));
  }

  async function agir(acao: () => Promise<unknown>) {
    const sessao = lerSessao();
    if (!sessao) return;

    try {
      await acao();
      await carregar();
      if (aberto) {
        const pagina = await api.entregasDoWebhook(sessao.token, aberto);
        setEntregas((atual) => ({ ...atual, [aberto]: pagina.itens }));
      }
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "A operacao nao foi concluida.");
    }
  }

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">Integracao</span>
          <h2>Webhooks</h2>
        </div>
      </div>

      <section className="acoes-caixa" style={{ marginBottom: "2rem" }}>
        <h3>Como conferir a assinatura</h3>
        <p>
          Cada envio leva o cabecalho <code>Aval-Assinatura</code> no formato{" "}
          <code>t=&lt;instante&gt;,v1=&lt;hmac&gt;</code>. O que foi assinado nao e so o corpo, e a juncao
          do instante com o corpo, separados por ponto. Do seu lado, refaca o HMAC SHA-256 com o segredo,
          compare em tempo constante e recuse o que tiver mais de cinco minutos: e isso que impede alguem
          de repetir uma requisicao capturada.
        </p>
        <p>
          Entrega sem resposta 2xx volta para a fila com espera crescente, de um minuto a seis horas.
          Depois disso fica registrada como falha, esperando reenvio.
        </p>
      </section>

      <form className="formulario" onSubmit={cadastrar} style={{ marginBottom: "2.4rem" }}>
        <div className="dupla">
          <label className="campo">
            <span>URL do endpoint</span>
            <input
              value={url}
              onChange={(evento) => setUrl(evento.target.value)}
              placeholder="https://seu-servidor.com/eventos/aval"
              required
            />
          </label>
          <label className="campo">
            <span>Descricao</span>
            <input
              value={descricao}
              onChange={(evento) => setDescricao(evento.target.value)}
              placeholder="ambiente de producao"
            />
          </label>
        </div>

        {erro && <p className="aviso">{erro}</p>}

        {segredoNovo && (
          <div className="aviso" data-tom="ok">
            <p>Endpoint cadastrado. Guarde o segredo agora: ele nao aparece inteiro de novo.</p>
            <p className="segredo" style={{ marginTop: "0.6rem" }}>{segredoNovo}</p>
          </div>
        )}

        <button className="botao" type="submit" disabled={enviando}>
          {enviando ? "Cadastrando" : "Cadastrar endpoint"}
        </button>
      </form>

      {carregando ? (
        <p className="carregando">Carregando</p>
      ) : endpoints.length === 0 ? (
        <div className="vazio">
          <p>Nenhum endpoint cadastrado. Sem eles, as cobrancas so aparecem quando voce consulta a API.</p>
        </div>
      ) : (
        endpoints.map((endpoint) => (
          <article className="cartao-endpoint" key={endpoint.codigo}>
            <header>
              <div>
                <h3>{endpoint.url}</h3>
                <span className="etiqueta">
                  {endpoint.descricao ? `${endpoint.descricao} · ` : ""}
                  {endpoint.ativo ? "recebendo eventos" : "desligado"}
                </span>
              </div>
              <div className="acoes-botoes">
                <button
                  className="botao"
                  data-tom="vazado"
                  type="button"
                  onClick={() => agir(() => api.alternarWebhook(lerSessao()!.token, endpoint.codigo, !endpoint.ativo))}
                >
                  {endpoint.ativo ? "Desligar" : "Ligar"}
                </button>
                <button
                  className="botao"
                  data-tom="vazado"
                  type="button"
                  onClick={() => abrirEntregas(endpoint.codigo)}
                >
                  {aberto === endpoint.codigo ? "Fechar entregas" : "Ver entregas"}
                </button>
                <button
                  className="botao"
                  data-tom="risco"
                  type="button"
                  onClick={() => agir(() => api.removerWebhook(lerSessao()!.token, endpoint.codigo))}
                >
                  Remover
                </button>
              </div>
            </header>

            <p className="segredo">{endpoint.segredo}</p>

            {aberto === endpoint.codigo && (
              <div className="entregas">
                {(entregas[endpoint.codigo] ?? []).length === 0 ? (
                  <p className="grafico-vazio">Nenhuma entrega ainda.</p>
                ) : (
                  (entregas[endpoint.codigo] ?? []).map((entrega) => (
                    <div className="entrega" key={entrega.codigo}>
                      <span className="entrega-evento">
                        {entrega.evento}
                        <span className="entrega-detalhe">
                          {dataHora(entrega.criadoEm)}
                          {entrega.ultimaFalha ? ` · ${entrega.ultimaFalha}` : ""}
                        </span>
                      </span>
                      <span className="selo" data-estado={situacaoParaSelo(entrega.situacao)}>
                        {entrega.situacao}
                      </span>
                      <span className="razao-valor">{entrega.tentativas}x</span>
                      <button
                        className="botao"
                        data-tom="vazado"
                        type="button"
                        onClick={() => agir(() => api.reenviarEntrega(lerSessao()!.token, entrega.codigo))}
                      >
                        Reenviar
                      </button>
                    </div>
                  ))
                )}
              </div>
            )}
          </article>
        ))
      )}

      <section className="acoes-caixa" style={{ marginTop: "2rem" }}>
        <h3>Eventos emitidos</h3>
        <LinhaConta rotulo="cobranca.autorizada" valor="valor reservado, aguardando captura" />
        <LinhaConta rotulo="cobranca.capturada" valor="dinheiro efetivamente cobrado" />
        <LinhaConta rotulo="cobranca.recusada" valor="o emissor negou" />
        <LinhaConta rotulo="cobranca.cancelada" valor="autorizacao desfeita antes da captura" />
        <LinhaConta rotulo="cobranca.estornada" valor="devolucao total ou parcial" />
      </section>
    </>
  );
}

function situacaoParaSelo(situacao: string): string {
  if (situacao === "ENTREGUE") return "CAPTURADA";
  if (situacao === "FALHOU") return "RECUSADA";
  return "AUTORIZADA";
}
