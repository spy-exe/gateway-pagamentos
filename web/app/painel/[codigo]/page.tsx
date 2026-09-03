"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import { useParams } from "next/navigation";
import LinhaConta from "@/components/LinhaConta";
import QrPix from "@/components/QrPix";
import SeloDeAval from "@/components/SeloDeAval";
import { api, ErroDaApi, type Cobranca, type Estorno, type Evento } from "@/lib/api";
import { dataHora, horario, moeda, parcelamento, rotuloBandeira, rotuloEvento, rotuloMetodo, rotuloStatus } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

export default function DetalheDaCobranca() {
  const parametros = useParams<{ codigo: string }>();
  const codigo = parametros.codigo;

  const [cobranca, setCobranca] = useState<Cobranca | null>(null);
  const [eventos, setEventos] = useState<Evento[]>([]);
  const [estornos, setEstornos] = useState<Estorno[]>([]);
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);
  const [recado, setRecado] = useState<string | null>(null);
  const [ocupado, setOcupado] = useState(false);
  const [valorDoEstorno, setValorDoEstorno] = useState("");

  const carregar = useCallback(async () => {
    const sessao = lerSessao();
    if (!sessao) return;

    try {
      const [dados, linha, devolucoes] = await Promise.all([
        api.buscarCobranca(sessao.token, codigo),
        api.eventos(sessao.token, codigo),
        api.estornos(sessao.token, codigo)
      ]);
      setCobranca(dados);
      setEventos(linha);
      setEstornos(devolucoes);
      setErro(null);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel carregar a cobranca.");
    } finally {
      setCarregando(false);
    }
  }, [codigo]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function executar(acao: () => Promise<unknown>, sucesso: string) {
    setOcupado(true);
    setErro(null);
    setRecado(null);

    try {
      await acao();
      setRecado(sucesso);
      await carregar();
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "A operacao nao foi concluida.");
    } finally {
      setOcupado(false);
    }
  }

  if (carregando) return <p className="carregando">Carregando</p>;

  if (!cobranca) {
    return (
      <div className="vazio">
        <p>{erro ?? "Cobranca nao encontrada."}</p>
        <Link className="botao" href="/painel">
          Voltar para a lista
        </Link>
      </div>
    );
  }

  const sessao = lerSessao();
  const podeCapturar = cobranca.status === "AUTORIZADA";
  const podeCancelar = cobranca.status === "AUTORIZADA";
  const podeEstornar = cobranca.status === "CAPTURADA" || cobranca.status === "PARCIALMENTE_ESTORNADA";

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">
            <Link href="/painel">Cobrancas</Link> / {codigo.slice(0, 12)}
          </span>
          <h2>{cobranca.descricao}</h2>
        </div>
      </div>

      <div className="detalhe">
        <article className="comprovante">
          <header className="comprovante-topo">
            <h2>Comprovante</h2>
            <p>{sessao?.nomeEstabelecimento ?? "Estabelecimento"}</p>
          </header>

          <p className="comprovante-valor">{moeda(cobranca.valorEmCentavos)}</p>
          <p className="comprovante-veredito">
            {rotuloStatus(cobranca.status)}
            {cobranca.descricaoDaRecusa ? ` · ${cobranca.descricaoDaRecusa}` : ""}
          </p>

          <SeloDeAval
            key={cobranca.status}
            codigoAutorizacao={cobranca.codigoAutorizacao}
            quando={cobranca.atualizadoEm}
            recusado={cobranca.status === "RECUSADA" || cobranca.status === "CANCELADA"}
          />

          <div className="comprovante-bloco">
            <LinhaConta rotulo="Codigo" valor={cobranca.codigo.replace("cob_", "").slice(0, 16)} />
            <LinhaConta rotulo="Metodo" valor={rotuloMetodo(cobranca.metodo)} />
            {cobranca.parcelas > 1 && (
              <LinhaConta
                rotulo="Parcelamento"
                valor={parcelamento(
                  cobranca.parcelas,
                  cobranca.valorDaParcelaEmCentavos,
                  cobranca.ajusteNaPrimeiraParcelaEmCentavos
                )}
              />
            )}
            {cobranca.codigoAutorizacao && (
              <LinhaConta rotulo="Autorizacao" valor={cobranca.codigoAutorizacao} />
            )}
            {cobranca.cartao && (
              <>
                <LinhaConta rotulo="Bandeira" valor={rotuloBandeira(cobranca.cartao.bandeira)} />
                <LinhaConta rotulo="Cartao" valor={`**** ${cobranca.cartao.ultimosQuatro}`} />
                {cobranca.cartao.nomePortador && (
                  <LinhaConta rotulo="Portador" valor={cobranca.cartao.nomePortador} />
                )}
              </>
            )}
            <LinhaConta rotulo="Captura" valor={cobranca.capturaAutomatica ? "Automatica" : "Manual"} />
            <LinhaConta rotulo="Criada em" valor={dataHora(cobranca.criadoEm)} />
          </div>

          {cobranca.valorEstornadoEmCentavos > 0 && (
            <div className="comprovante-bloco">
              <LinhaConta rotulo="Estornado" valor={`- ${moeda(cobranca.valorEstornadoEmCentavos)}`} />
              <LinhaConta rotulo="Saldo" valor={moeda(cobranca.saldoEstornavelEmCentavos)} />
            </div>
          )}

          <div className="comprovante-bloco comprovante-tempo">
            {eventos.map((evento, indice) => (
              <div className="comprovante-evento" key={`${evento.tipo}-${indice}`}>
                <div>
                  <strong>{rotuloEvento(evento.tipo)}</strong>
                  <time dateTime={evento.criadoEm}>{horario(evento.criadoEm)}</time>
                </div>
                {evento.detalhe && <p>{evento.detalhe}</p>}
              </div>
            ))}
          </div>

          {cobranca.pixCopiaECola && (
            <div className="comprovante-bloco">
              <p className="etiqueta" style={{ textAlign: "center" }}>Pague com Pix</p>
              <QrPix copiaECola={cobranca.pixCopiaECola} />
            </div>
          )}

          <p className="comprovante-rodape">Transacao simulada · sem valor fiscal</p>
        </article>

        <div className="acoes">
          {recado && (
            <p className="aviso" data-tom="ok">
              {recado}
            </p>
          )}
          {erro && <p className="aviso">{erro}</p>}

          {podeCapturar || podeCancelar ? (
            <section className="acoes-caixa">
              <h3>Autorizada, aguardando decisao</h3>
              <p>
                O valor esta reservado no cartao. Capture para cobrar de fato, ou cancele para devolver o
                limite ao portador.
              </p>
              <div className="acoes-botoes">
                <button
                  className="botao"
                  type="button"
                  disabled={ocupado}
                  onClick={() =>
                    executar(
                      () => api.capturar(lerSessao()!.token, cobranca.codigo),
                      "Cobranca capturada."
                    )
                  }
                >
                  Capturar
                </button>
                <button
                  className="botao"
                  data-tom="risco"
                  type="button"
                  disabled={ocupado}
                  onClick={() =>
                    executar(
                      () => api.cancelar(lerSessao()!.token, cobranca.codigo),
                      "Autorizacao cancelada."
                    )
                  }
                >
                  Cancelar
                </button>
              </div>
            </section>
          ) : null}

          {podeEstornar && (
            <section className="acoes-caixa">
              <h3>Estorno</h3>
              <p>
                Disponivel para estorno: {moeda(cobranca.saldoEstornavelEmCentavos)}. Deixe o campo vazio
                para devolver tudo de uma vez.
              </p>
              <label className="campo" data-mono="sim">
                <span>Valor do estorno</span>
                <input
                  inputMode="numeric"
                  value={valorDoEstorno ? moeda(Number(valorDoEstorno)) : ""}
                  onChange={(evento) => setValorDoEstorno(evento.target.value.replace(/\D/g, "").slice(0, 9))}
                  placeholder="tudo"
                />
              </label>
              <div className="acoes-botoes">
                <button
                  className="botao"
                  type="button"
                  disabled={ocupado}
                  onClick={() =>
                    executar(async () => {
                      await api.estornar(lerSessao()!.token, cobranca.codigo, {
                        valorEmCentavos: valorDoEstorno ? Number(valorDoEstorno) : undefined
                      });
                      setValorDoEstorno("");
                    }, "Estorno registrado.")
                  }
                >
                  Estornar
                </button>
              </div>
            </section>
          )}

          {estornos.length > 0 && (
            <section className="acoes-caixa">
              <h3>Estornos registrados</h3>
              {estornos.map((estorno) => (
                <LinhaConta
                  key={estorno.codigo}
                  rotulo={dataHora(estorno.criadoEm)}
                  valor={moeda(estorno.valorEmCentavos)}
                />
              ))}
            </section>
          )}

          {!podeCapturar && !podeEstornar && (
            <section className="acoes-caixa">
              <h3>Transacao encerrada</h3>
              <p>
                {cobranca.status === "RECUSADA"
                  ? `O emissor simulado recusou: ${cobranca.descricaoDaRecusa ?? "sem detalhe"}.`
                  : "Nao ha mais acao possivel nesta cobranca."}
              </p>
              <div className="acoes-botoes">
                <Link className="botao" data-tom="vazado" href="/painel/nova">
                  Criar outra
                </Link>
              </div>
            </section>
          )}
        </div>
      </div>
    </>
  );
}
