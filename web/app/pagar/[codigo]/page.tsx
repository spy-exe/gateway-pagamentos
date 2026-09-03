"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams } from "next/navigation";
import LinhaConta from "@/components/LinhaConta";
import Marca from "@/components/Marca";
import QrPix from "@/components/QrPix";
import { api, ErroDaApi, type CheckoutLinkPagamento, type Cobranca } from "@/lib/api";
import { agruparNumero, moeda, rotuloMetodo } from "@/lib/formato";

const CARTAO_APROVADO = "4111111111111111";

export default function CheckoutPublico() {
  const { codigo } = useParams<{ codigo: string }>();
  const chave = useRef(novaChave());
  const [link, setLink] = useState<CheckoutLinkPagamento | null>(null);
  const [cobranca, setCobranca] = useState<Cobranca | null>(null);
  const [numero, setNumero] = useState("");
  const [nomePortador, setNomePortador] = useState("");
  const [validadeMes, setValidadeMes] = useState("12");
  const [validadeAno, setValidadeAno] = useState("2030");
  const [parcelas, setParcelas] = useState(1);
  const [erro, setErro] = useState<string | null>(null);
  const [carregando, setCarregando] = useState(true);
  const [enviando, setEnviando] = useState(false);

  const carregar = useCallback(async () => {
    try {
      setLink(await api.abrirLink(codigo));
      setErro(null);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel abrir este link.");
    } finally {
      setCarregando(false);
    }
  }, [codigo]);

  useEffect(() => {
    void carregar();
  }, [carregar]);

  async function pagar(evento: React.FormEvent) {
    evento.preventDefault();
    if (!link) return;

    setEnviando(true);
    setErro(null);

    try {
      const resultado = await api.finalizarLink(
        codigo,
        {
          parcelas: link.metodo === "CARTAO_CREDITO" ? parcelas : 1,
          cartao: link.metodo === "PIX"
            ? undefined
            : {
                numero: numero.replace(/\D/g, ""),
                validadeMes: Number(validadeMes),
                validadeAno: Number(validadeAno),
                nomePortador
              }
        },
        chave.current
      );
      setCobranca(resultado);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "O pagamento nao foi concluido.");
    } finally {
      setEnviando(false);
    }
  }

  function tentarNovamente() {
    chave.current = novaChave();
    setCobranca(null);
    setErro(null);
  }

  if (carregando) {
    return <main className="checkout-publico"><p className="carregando">Abrindo pagamento</p></main>;
  }

  if (!link) {
    return (
      <main className="checkout-publico">
        <div className="checkout-indisponivel">
          <Marca complemento="Pagamento" />
          <span className="etiqueta">Link indisponivel</span>
          <h1>Nao encontramos esta cobranca.</h1>
          <p>{erro ?? "Confira o endereco recebido e tente novamente."}</p>
        </div>
      </main>
    );
  }

  if (link.situacao !== "ATIVO") {
    return (
      <main className="checkout-publico">
        <div className="checkout-indisponivel">
          <Marca complemento={link.estabelecimento} />
          <span className="etiqueta">Pagamento encerrado</span>
          <h1>Este link nao esta mais recebendo.</h1>
          <p>Peça um novo link diretamente ao estabelecimento.</p>
        </div>
      </main>
    );
  }

  const aprovado = cobranca && cobranca.status !== "RECUSADA";

  return (
    <main className="checkout-publico">
      <section className="checkout-resumo">
        <Marca complemento={link.estabelecimento} />
        <div className="checkout-oferta">
          <span className="etiqueta">Pedido</span>
          <h1>{link.descricao}</h1>
          <p className="checkout-valor">{moeda(link.valorEmCentavos)}</p>
          <LinhaConta rotulo="Recebedor" valor={link.estabelecimento} />
          <LinhaConta rotulo="Pagamento" valor={rotuloMetodo(link.metodo)} />
          {link.parcelasMaximas > 1 && (
            <LinhaConta rotulo="Condicao" valor={`ate ${link.parcelasMaximas} parcelas`} />
          )}
        </div>
        <p className="checkout-seguranca">
          O numero completo do cartao e usado apenas durante a autorizacao e nao fica gravado.
          Esta demonstracao nao movimenta dinheiro real.
        </p>
      </section>

      <section className="checkout-pagamento">
        {cobranca ? (
          <div className="checkout-resultado" data-aprovado={aprovado ? "sim" : "nao"}>
            <span className="etiqueta">{aprovado ? "Pagamento confirmado" : "Pagamento recusado"}</span>
            <h2>{aprovado ? "Tudo certo por aqui." : "O emissor nao deu o aval."}</h2>
            <p>
              {aprovado
                ? `A confirmacao ${cobranca.codigo.slice(0, 16)} ja foi enviada para ${link.estabelecimento}.`
                : cobranca.descricaoDaRecusa ?? "Use outro cartao e tente novamente."}
            </p>

            {cobranca.pixCopiaECola && <QrPix copiaECola={cobranca.pixCopiaECola} />}

            <div className="checkout-recibo">
              <LinhaConta rotulo="Valor" valor={moeda(cobranca.valorEmCentavos)} peso="forte" />
              <LinhaConta rotulo="Situacao" valor={aprovado ? "Confirmado" : "Recusado"} />
              {cobranca.codigoAutorizacao && (
                <LinhaConta rotulo="Autorizacao" valor={cobranca.codigoAutorizacao} />
              )}
            </div>

            {!aprovado && (
              <button className="botao" type="button" onClick={tentarNovamente}>
                Tentar outro cartao
              </button>
            )}
          </div>
        ) : (
          <form className="formulario checkout-formulario" onSubmit={pagar}>
            <div>
              <span className="etiqueta">Finalizar</span>
              <h2>{link.metodo === "PIX" ? "Gerar Pix" : "Dados do cartao"}</h2>
            </div>

            {link.metodo !== "PIX" && (
              <>
                <label className="campo" data-mono="sim">
                  <span>Numero do cartao</span>
                  <input
                    inputMode="numeric"
                    autoComplete="cc-number"
                    value={agruparNumero(numero)}
                    onChange={(evento) => setNumero(evento.target.value.replace(/\D/g, "").slice(0, 19))}
                    placeholder="4111 1111 1111 1111"
                    required
                  />
                </label>

                <label className="campo">
                  <span>Nome impresso no cartao</span>
                  <input
                    autoComplete="cc-name"
                    value={nomePortador}
                    onChange={(evento) => setNomePortador(evento.target.value)}
                    placeholder="Nome do portador"
                    required
                  />
                </label>

                <div className="dupla">
                  <label className="campo" data-mono="sim">
                    <span>Mes</span>
                    <input
                      inputMode="numeric"
                      autoComplete="cc-exp-month"
                      value={validadeMes}
                      onChange={(evento) => setValidadeMes(evento.target.value.replace(/\D/g, "").slice(0, 2))}
                      required
                    />
                  </label>
                  <label className="campo" data-mono="sim">
                    <span>Ano</span>
                    <input
                      inputMode="numeric"
                      autoComplete="cc-exp-year"
                      value={validadeAno}
                      onChange={(evento) => setValidadeAno(evento.target.value.replace(/\D/g, "").slice(0, 4))}
                      required
                    />
                  </label>
                </div>

                {link.metodo === "CARTAO_CREDITO" && link.parcelasMaximas > 1 && (
                  <label className="campo">
                    <span>Parcelamento</span>
                    <select value={parcelas} onChange={(evento) => setParcelas(Number(evento.target.value))}>
                      {Array.from({ length: link.parcelasMaximas }, (_, indice) => indice + 1).map((quantidade) => (
                        <option key={quantidade} value={quantidade}>
                          {quantidade === 1
                            ? `A vista · ${moeda(link.valorEmCentavos)}`
                            : `${quantidade}x de ${moeda(Math.floor(link.valorEmCentavos / quantidade))}`}
                        </option>
                      ))}
                    </select>
                  </label>
                )}

                <button
                  className="atalho-checkout"
                  type="button"
                  onClick={() => {
                    setNumero(CARTAO_APROVADO);
                    setNomePortador("Cliente de demonstracao");
                  }}
                >
                  Usar cartao de demonstracao aprovado
                </button>
              </>
            )}

            {link.metodo === "PIX" && (
              <p className="checkout-instrucao">
                Ao continuar, o codigo Pix sera preparado com o valor exato e a identificacao deste pedido.
              </p>
            )}

            {erro && <p className="aviso">{erro}</p>}

            <button className="botao" data-largura="cheia" type="submit" disabled={enviando}>
              {enviando ? "Pedindo o aval" : link.metodo === "PIX" ? "Gerar codigo Pix" : `Pagar ${moeda(link.valorEmCentavos)}`}
            </button>
          </form>
        )}
      </section>
    </main>
  );
}

function novaChave(): string {
  return typeof crypto !== "undefined" ? crypto.randomUUID() : String(Date.now());
}
