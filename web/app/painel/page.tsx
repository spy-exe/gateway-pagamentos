"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import Link from "next/link";
import GraficoDeBandeiras from "@/components/GraficoDeBandeiras";
import GraficoDeVolume from "@/components/GraficoDeVolume";
import LinhaConta from "@/components/LinhaConta";
import Selo from "@/components/Selo";
import {
  api,
  ErroDaApi,
  type Cobranca,
  type DiaDoMovimento,
  type FatiaDeBandeira,
  type ResumoDoPeriodo,
  type StatusCobranca
} from "@/lib/api";
import { dataCurta, extratoEmCsv, moeda, rotuloMetodo } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

const FILTROS: Array<{ chave: string; rotulo: string }> = [
  { chave: "", rotulo: "Todas" },
  { chave: "AUTORIZADA", rotulo: "Autorizadas" },
  { chave: "CAPTURADA", rotulo: "Capturadas" },
  { chave: "RECUSADA", rotulo: "Recusadas" },
  { chave: "ESTORNADA", rotulo: "Estornadas" }
];

const PERIODOS = [
  { dias: 7, rotulo: "7 dias" },
  { dias: 30, rotulo: "30 dias" },
  { dias: 90, rotulo: "90 dias" }
];

export default function ListaDeCobrancas() {
  const [cobrancas, setCobrancas] = useState<Cobranca[]>([]);
  const [resumo, setResumo] = useState<ResumoDoPeriodo | null>(null);
  const [movimento, setMovimento] = useState<DiaDoMovimento[]>([]);
  const [bandeiras, setBandeiras] = useState<FatiaDeBandeira[]>([]);
  const [filtro, setFiltro] = useState("");
  const [dias, setDias] = useState(30);
  const [busca, setBusca] = useState("");
  const [buscaAplicada, setBuscaAplicada] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (status: string, janela: number, termo: string) => {
    const sessao = lerSessao();
    if (!sessao) return;

    setCarregando(true);
    setErro(null);

    const inicio = new Date();
    inicio.setDate(inicio.getDate() - janela);

    try {
      const [pagina, totais, porDia, porBandeira] = await Promise.all([
        api.listarCobrancas(sessao.token, {
          status: status || undefined,
          busca: termo || undefined,
          de: inicio.toISOString()
        }),
        api.resumo(sessao.token, janela),
        api.movimento(sessao.token, janela),
        api.bandeiras(sessao.token, janela)
      ]);

      setCobrancas(pagina.itens);
      setResumo(totais);
      setMovimento(porDia);
      setBandeiras(porBandeira);
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel carregar as cobrancas.");
    } finally {
      setCarregando(false);
    }
  }, []);

  useEffect(() => {
    void carregar(filtro, dias, buscaAplicada);
  }, [carregar, filtro, dias, buscaAplicada]);

  const csv = useMemo(() => extratoEmCsv(cobrancas as unknown as Array<Record<string, unknown>>), [cobrancas]);

  function baixarExtrato() {
    const arquivo = new Blob([`﻿${csv}`], { type: "text/csv;charset=utf-8" });
    const endereco = URL.createObjectURL(arquivo);
    const ancora = document.createElement("a");
    ancora.href = endereco;
    ancora.download = `extrato-aval-${new Date().toISOString().slice(0, 10)}.csv`;
    ancora.click();
    URL.revokeObjectURL(endereco);
  }

  return (
    <>
      <div className="cabecalho-secao">
        <div>
          <span className="etiqueta">Movimento</span>
          <h2>Cobrancas</h2>
        </div>
        <div className="acoes-botoes">
          <button className="botao" data-tom="vazado" type="button" onClick={baixarExtrato}
                  disabled={cobrancas.length === 0}>
            Baixar extrato
          </button>
          <Link className="botao" href="/painel/nova">
            Nova cobranca
          </Link>
        </div>
      </div>

      <div className="filtros">
        {PERIODOS.map((periodo) => (
          <button
            key={periodo.dias}
            className="filtro"
            type="button"
            data-ativo={dias === periodo.dias ? "sim" : "nao"}
            onClick={() => setDias(periodo.dias)}
          >
            {periodo.rotulo}
          </button>
        ))}
      </div>

      <div className="paineis">
        <GraficoDeVolume dias={movimento} />
        <GraficoDeBandeiras fatias={bandeiras} />
      </div>

      <div className="fechamento">
        <LinhaConta rotulo="Capturado" valor={moeda(resumo?.capturadoEmCentavos ?? 0)} />
        <LinhaConta rotulo="Estornado" valor={`- ${moeda(resumo?.estornadoEmCentavos ?? 0)}`} />
        <LinhaConta rotulo="A capturar" valor={moeda(resumo?.autorizadoEmCentavos ?? 0)} />
        <LinhaConta
          rotulo="Aprovacao"
          valor={`${(resumo?.taxaDeAprovacao ?? 0).toFixed(2).replace(".", ",")}% de ${resumo?.total ?? 0}`}
        />
        <LinhaConta rotulo="Liquido" valor={moeda(resumo?.liquidoEmCentavos ?? 0)} peso="forte" />
      </div>

      <form
        className="barra-de-filtros"
        onSubmit={(evento) => {
          evento.preventDefault();
          setBuscaAplicada(busca);
        }}
      >
        <label className="campo">
          <span>Buscar</span>
          <input
            value={busca}
            onChange={(evento) => setBusca(evento.target.value)}
            placeholder="descricao ou codigo"
          />
        </label>
        <button className="botao" data-tom="vazado" type="submit">
          Filtrar
        </button>
        {buscaAplicada && (
          <button
            className="botao"
            data-tom="vazado"
            type="button"
            onClick={() => {
              setBusca("");
              setBuscaAplicada("");
            }}
          >
            Limpar
          </button>
        )}
      </form>

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
            {filtro || buscaAplicada
              ? "Nenhuma cobranca encontrada com esses filtros."
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
                  {cobranca.parcelas > 1 ? ` · ${cobranca.parcelas}x` : ""}
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
