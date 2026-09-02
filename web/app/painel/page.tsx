"use client";

import { useCallback, useEffect, useState } from "react";
import Link from "next/link";
import LinhaConta from "@/components/LinhaConta";
import Selo from "@/components/Selo";
import { api, ErroDaApi, type Cobranca, type StatusCobranca } from "@/lib/api";
import { dataCurta, moeda, rotuloMetodo } from "@/lib/formato";
import { calcularFechamento } from "@/lib/fechamento";
import { lerSessao } from "@/lib/sessao";

const FILTROS: Array<{ chave: string; rotulo: string }> = [
  { chave: "", rotulo: "Todas" },
  { chave: "AUTORIZADA", rotulo: "Autorizadas" },
  { chave: "CAPTURADA", rotulo: "Capturadas" },
  { chave: "RECUSADA", rotulo: "Recusadas" },
  { chave: "ESTORNADA", rotulo: "Estornadas" }
];

export default function ListaDeCobrancas() {
  const [cobrancas, setCobrancas] = useState<Cobranca[]>([]);
  const [filtro, setFiltro] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (status: string) => {
    const sessao = lerSessao();
    if (!sessao) return;

    setCarregando(true);
    setErro(null);

    try {
      const pagina = await api.listarCobrancas(sessao.token, { status: status || undefined });
      setCobrancas(pagina.itens);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel carregar as cobrancas.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar(filtro);
  }, [carregar, filtro]);

  const fechamento = calcularFechamento(cobrancas);

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">Movimento</span>
          <h2>Cobrancas</h2>
        </div>
        <Link className="botao" href="/painel/nova">
          Nova cobranca
        </Link>
      </div>

      <div className="fechamento">
        <LinhaConta rotulo="Capturado" valor={moeda(fechamento.capturado)} />
        <LinhaConta rotulo="Estornado" valor={`- ${moeda(fechamento.estornado)}`} />
        <LinhaConta rotulo="Recusadas" valor={String(fechamento.recusadas)} />
        <LinhaConta rotulo="Liquido" valor={moeda(fechamento.liquido)} peso="forte" />
      </div>

      <div className="filtros">
        {FILTROS.map((opcao) => (
          <button
            key={opcao.chave || "todas"}
            className="filtro"
            type="button"
            data-ativo={filtro === opcao.chave ? "sim" : "nao"}
            onClick={() => setFiltro(opcao.chave)}
          >
            {opcao.rotulo}
          </button>
        ))}
      </div>

      {erro && <p className="aviso">{erro}</p>}

      {carregando ? (
        <p className="carregando">Carregando</p>
      ) : cobrancas.length === 0 ? (
        <div className="vazio">
          <p>
            {filtro
              ? "Nenhuma cobranca com esse status ainda."
              : "Voce ainda nao criou nenhuma cobranca."}
          </p>
          <Link className="botao" href="/painel/nova">
            Criar a primeira
          </Link>
        </div>
      ) : (
        <div className="razao">
          {cobrancas.map((cobranca, indice) => (
            <Link
              className="razao-linha entra"
              style={{ animationDelay: `${Math.min(indice, 8) * 45}ms` }}
              key={cobranca.codigo}
              href={`/painel/${cobranca.codigo}`}
            >
              <time className="razao-data" dateTime={cobranca.criadoEm}>
                {dataCurta(cobranca.criadoEm)}
              </time>

              <span className="razao-descricao">
                <strong>{cobranca.descricao}</strong>
                <span>
                  {rotuloMetodo(cobranca.metodo)}
                  {cobranca.cartao ? ` ${cobranca.cartao.bandeira} ${cobranca.cartao.ultimosQuatro}` : ""}
                </span>
              </span>

              <span className="razao-selo">
                <Selo estado={cobranca.status as StatusCobranca} />
              </span>

              <span className="razao-valor">{moeda(cobranca.valorEmCentavos)}</span>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
