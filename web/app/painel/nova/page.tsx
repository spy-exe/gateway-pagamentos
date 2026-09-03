"use client";

import { useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import PlacaGravada from "@/components/PlacaGravada";
import { api, ErroDaApi, type MetodoPagamento } from "@/lib/api";
import { agruparNumero, bandeiraDoNumero, moeda, parcelamento } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

const CARTOES_DE_TESTE = [
  { numero: "4111111111111111", efeito: "aprova, Visa" },
  { numero: "5555555555554444", efeito: "aprova, Mastercard" },
  { numero: "5099990000000003", efeito: "aprova, Elo" },
  { numero: "4111000000080000", efeito: "recusa, cartao bloqueado" },
  { numero: "4111000000070001", efeito: "recusa, saldo insuficiente" },
  { numero: "9999000000000004", efeito: "recusa, bandeira nao suportada" }
];

export default function NovaCobranca() {
  const router = useRouter();
  const chave = useRef(typeof crypto !== "undefined" ? crypto.randomUUID() : String(Date.now()));

  const [centavos, setCentavos] = useState("");
  const [descricao, setDescricao] = useState("");
  const [metodo, setMetodo] = useState<MetodoPagamento>("CARTAO_CREDITO");
  const [capturaAutomatica, setCapturaAutomatica] = useState(true);
  const [parcelas, setParcelas] = useState(1);
  const [numero, setNumero] = useState("");
  const [validadeMes, setValidadeMes] = useState("12");
  const [validadeAno, setValidadeAno] = useState("2030");
  const [nomePortador, setNomePortador] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);

  const usaCartao = metodo !== "PIX";
  const bandeira = useMemo(() => bandeiraDoNumero(numero), [numero]);
  const valorEmCentavos = Number(centavos || "0");

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    setCampos({});

    const sessao = lerSessao();
    if (!sessao) return;

    if (valorEmCentavos <= 0) {
      setCampos({ valorEmCentavos: "informe um valor maior que zero" });
      return;
    }

    setEnviando(true);

    try {
      const cobranca = await api.criarCobranca(
        sessao.token,
        {
          valorEmCentavos,
          descricao,
          metodo,
          capturaAutomatica,
          parcelas: metodo === "CARTAO_CREDITO" ? parcelas : 1,
          cartao: usaCartao
            ? {
                numero: numero.replace(/\D/g, ""),
                validadeMes: Number(validadeMes),
                validadeAno: Number(validadeAno),
                nomePortador
              }
            : undefined
        },
        chave.current
      );

      router.push(`/painel/${cobranca.codigo}`);
    } catch (falha) {
      if (falha instanceof ErroDaApi) {
        setErro(falha.message);
        setCampos(falha.campos);
      } else {
        setErro("Nao foi possivel criar a cobranca.");
      }
      setEnviando(false);
    }
  }

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">Nova transacao</span>
          <h2>Criar cobranca</h2>
        </div>
      </div>

      <div className="oficina">
        <form className="formulario" onSubmit={enviar}>
          <div className="dupla">
            <label className="campo" data-mono="sim">
              <span>Valor</span>
              <input
                inputMode="numeric"
                value={centavos ? moeda(valorEmCentavos) : ""}
                onChange={(evento) => setCentavos(evento.target.value.replace(/\D/g, "").slice(0, 9))}
                placeholder="R$ 0,00"
              />
              {campos.valorEmCentavos && <em className="campo-erro">{campos.valorEmCentavos}</em>}
            </label>

            <label className="campo">
              <span>Metodo</span>
              <select value={metodo} onChange={(evento) => setMetodo(evento.target.value as MetodoPagamento)}>
                <option value="CARTAO_CREDITO">Cartao de credito</option>
                <option value="CARTAO_DEBITO">Cartao de debito</option>
                <option value="PIX">Pix</option>
              </select>
            </label>
          </div>

          <label className="campo">
            <span>Descricao</span>
            <input
              value={descricao}
              onChange={(evento) => setDescricao(evento.target.value)}
              placeholder="Encomenda de bolo"
              maxLength={140}
              required
            />
            {campos.descricao && <em className="campo-erro">{campos.descricao}</em>}
          </label>

          {usaCartao && (
            <>
              <label className="campo" data-mono="sim">
                <span>Numero do cartao</span>
                <input
                  inputMode="numeric"
                  value={agruparNumero(numero)}
                  onChange={(evento) => setNumero(evento.target.value.replace(/\D/g, "").slice(0, 19))}
                  placeholder="4111 1111 1111 1111"
                  autoComplete="off"
                  required
                />
              </label>

              <div className="dupla">
                <label className="campo" data-mono="sim">
                  <span>Mes</span>
                  <input
                    inputMode="numeric"
                    value={validadeMes}
                    onChange={(evento) => setValidadeMes(evento.target.value.replace(/\D/g, "").slice(0, 2))}
                    required
                  />
                </label>
                <label className="campo" data-mono="sim">
                  <span>Ano</span>
                  <input
                    inputMode="numeric"
                    value={validadeAno}
                    onChange={(evento) => setValidadeAno(evento.target.value.replace(/\D/g, "").slice(0, 4))}
                    required
                  />
                </label>
              </div>

              <label className="campo">
                <span>Nome impresso no cartao</span>
                <input
                  value={nomePortador}
                  onChange={(evento) => setNomePortador(evento.target.value)}
                  placeholder="Ricardo Figueiredo"
                  required
                />
              </label>
            </>
          )}

          {metodo === "CARTAO_CREDITO" && (
            <label className="campo">
              <span>Parcelamento</span>
              <select value={parcelas} onChange={(evento) => setParcelas(Number(evento.target.value))}>
                {Array.from({ length: 12 }, (_, indice) => indice + 1).map((quantidade) => (
                  <option key={quantidade} value={quantidade} disabled={valorEmCentavos > 0 && valorEmCentavos / quantidade < 500}>
                    {quantidade === 1
                      ? "A vista"
                      : `${quantidade}x${valorEmCentavos > 0 ? ` de ${moeda(Math.floor(valorEmCentavos / quantidade))}` : ""}`}
                  </option>
                ))}
              </select>
              {parcelas > 1 && valorEmCentavos > 0 && (
                <em className="campo-erro" style={{ color: "var(--tinta-fraca)" }}>
                  {parcelamento(parcelas, Math.floor(valorEmCentavos / parcelas), valorEmCentavos % parcelas)}
                </em>
              )}
            </label>
          )}

          <label className="campo">
            <span>Captura</span>
            <span style={{ display: "flex", alignItems: "center", gap: "0.6rem" }}>
              <input
                type="checkbox"
                checked={capturaAutomatica}
                onChange={(evento) => setCapturaAutomatica(evento.target.checked)}
                style={{ width: "auto" }}
              />
              <span style={{ color: "var(--texto-medio)", fontSize: "0.9rem" }}>
                Capturar assim que autorizar. Sem isso a cobranca fica autorizada esperando a captura.
              </span>
            </span>
          </label>

          {erro && <p className="aviso">{erro}</p>}

          <button className="botao" type="submit" disabled={enviando}>
            {enviando ? "Autorizando" : "Criar cobranca"}
          </button>
        </form>

        <aside className="bancada">
          <div className="bancada-palco">
            <PlacaGravada
              modo="bancada"
              altura={250}
              bandeira={bandeira}
              numeroParcial={usaCartao ? agruparNumero(numero) || "•••• •••• •••• ••••" : "PIX"}
              nomePortador={nomePortador || "SEU NOME AQUI"}
              validade={`${validadeMes.padStart(2, "0")}/${validadeAno.slice(-2)}`}
            />
          </div>
          <p className="dica-arraste">Arraste para girar</p>

          {usaCartao && (
            <>
              <p className="etiqueta" style={{ marginTop: "1.4rem" }}>
                Cartoes para teste
              </p>
              <div className="atalhos">
                {CARTOES_DE_TESTE.map((cartao) => (
                  <button
                    key={cartao.numero}
                    className="atalho"
                    type="button"
                    onClick={() => {
                      setNumero(cartao.numero);
                      if (!nomePortador) setNomePortador("Ricardo Figueiredo");
                    }}
                  >
                    <span>{agruparNumero(cartao.numero)}</span>
                    <span>{cartao.efeito}</span>
                  </button>
                ))}
              </div>
            </>
          )}
        </aside>
      </div>
    </>
  );
}
