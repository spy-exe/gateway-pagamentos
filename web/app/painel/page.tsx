"use client";

import { useCallback, useEffect, useState } from "react";
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
  type MetodoPagamento,
  type ResumoDoPeriodo,
  type StatusCobranca
} from "@/lib/api";
import { dataCurta, moeda, rotuloMetodo } from "@/lib/formato";
import { lerSessao } from "@/lib/sessao";

const FILTROS: Array<{ chave: string; rotulo: string }> = [
  { chave: "", rotulo: "Todas" },
  { chave: "AUTORIZADA", rotulo: "Autorizadas" },
  { chave: "CAPTURADA", rotulo: "Capturadas" },
  { chave: "RECUSADA", rotulo: "Recusadas" },
  { chave: "CANCELADA", rotulo: "Canceladas" },
  { chave: "PARCIALMENTE_ESTORNADA", rotulo: "Estorno parcial" },
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
  const [metodo, setMetodo] = useState("");
  const [dias, setDias] = useState(30);
  const [busca, setBusca] = useState("");
  const [buscaAplicada, setBuscaAplicada] = useState("");
  const [valorMinimo, setValorMinimo] = useState("");
  const [valorMaximo, setValorMaximo] = useState("");
  const [carregando, setCarregando] = useState(true);
  const [baixando, setBaixando] = useState(false);
  const [paginaAtual, setPaginaAtual] = useState(0);
  const [totalDePaginas, setTotalDePaginas] = useState(0);
  const [totalDeItens, setTotalDeItens] = useState(0);
  const [erro, setErro] = useState<string | null>(null);

  const carregar = useCallback(async (
    status: string,
    forma: string,
    janela: number,
    termo: string,
    minimo: string,
    maximo: string,
    pagina: number
  ) => {
    const sessao = lerSessao();
    if (!sessao) return;

    setCarregando(true);
    setErro(null);

    const inicio = new Date();
    inicio.setDate(inicio.getDate() - janela);

    try {
      const [paginaDeCobrancas, totais, porDia, porBandeira] = await Promise.all([
        api.listarCobrancas(sessao.token, {
          status: status || undefined,
          metodo: forma || undefined,
          busca: termo || undefined,
          valorMinimo: minimo ? Number(minimo) : undefined,
          valorMaximo: maximo ? Number(maximo) : undefined,
          de: inicio.toISOString(),
          tamanho: 20,
          pagina
        }),
        api.resumo(sessao.token, janela),
        api.movimento(sessao.token, janela),
        api.bandeiras(sessao.token, janela)
      ]);

      setCobrancas(paginaDeCobrancas.itens);
      setTotalDePaginas(paginaDeCobrancas.totalDePaginas);
      setTotalDeItens(paginaDeCobrancas.totalDeItens);
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
    void carregar(filtro, metodo, dias, buscaAplicada, valorMinimo, valorMaximo, paginaAtual);
  }, [carregar, filtro, metodo, dias, buscaAplicada, valorMinimo, valorMaximo, paginaAtual]);

  async function baixarExtrato() {
    const sessao = lerSessao();
    if (!sessao) return;

    const inicio = new Date();
    inicio.setDate(inicio.getDate() - dias);
    setBaixando(true);
    setErro(null);

    let arquivo: Blob;
    try {
      arquivo = await api.baixarExtrato(sessao.token, {
        status: filtro || undefined,
        metodo: metodo || undefined,
        busca: buscaAplicada || undefined,
        valorMinimo: valorMinimo ? Number(valorMinimo) : undefined,
        valorMaximo: valorMaximo ? Number(valorMaximo) : undefined,
        de: inicio.toISOString()
      });
    } catch (falha) {
      setErro(falha instanceof ErroDaApi ? falha.message : "Nao foi possivel gerar o extrato.");
      setBaixando(false);
      return;
    }

    const endereco = URL.createObjectURL(arquivo);
    const ancora = document.createElement("a");
    ancora.href = endereco;
    ancora.download = `extrato-aval-${new Date().toISOString().slice(0, 10)}.csv`;
    ancora.click();
    URL.revokeObjectURL(endereco);
    setBaixando(false);
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
                  disabled={totalDeItens === 0 || baixando}>
            {baixando ? "Gerando..." : "Baixar extrato"}
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
            onClick={() => {
              setDias(periodo.dias);
              setPaginaAtual(0);
            }}
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
          setPaginaAtual(0);
          setBuscaAplicada(busca);
        }}
      >
        <label className="campo">
          <span>Buscar</span>
          <input
            value={busca}
            onChange={(evento) => setBusca(evento.target.value)}
            placeholder="codigo, autorizacao ou final do cartao"
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
              setPaginaAtual(0);
            }}
          >
            Limpar
          </button>
        )}
      </form>

      <div className="barra-de-filtros barra-de-filtros-secundaria">
        <label className="campo">
          <span>Meio de pagamento</span>
          <select
            value={metodo}
            onChange={(evento) => {
              setMetodo(evento.target.value as MetodoPagamento | "");
              setPaginaAtual(0);
            }}
          >
            <option value="">Todos os meios</option>
            <option value="PIX">Pix</option>
            <option value="CARTAO_CREDITO">Cartao de credito</option>
            <option value="CARTAO_DEBITO">Cartao de debito</option>
          </select>
        </label>
        <label className="campo" data-mono="sim">
          <span>Valor minimo</span>
          <input
            inputMode="numeric"
            value={valorMinimo ? moeda(Number(valorMinimo)) : ""}
            onChange={(evento) => {
              setValorMinimo(evento.target.value.replace(/\D/g, "").slice(0, 12));
              setPaginaAtual(0);
            }}
            placeholder="Sem minimo"
          />
        </label>
        <label className="campo" data-mono="sim">
          <span>Valor maximo</span>
          <input
            inputMode="numeric"
            value={valorMaximo ? moeda(Number(valorMaximo)) : ""}
            onChange={(evento) => {
              setValorMaximo(evento.target.value.replace(/\D/g, "").slice(0, 12));
              setPaginaAtual(0);
            }}
            placeholder="Sem maximo"
          />
        </label>
        <p aria-live="polite">
          {totalDeItens === 1 ? "1 cobranca encontrada" : `${totalDeItens} cobrancas encontradas`}
        </p>
      </div>

      <div className="filtros">
        {FILTROS.map((opcao) => (
          <button
            key={opcao.chave || "todas"}
            className="filtro"
            type="button"
            data-ativo={filtro === opcao.chave ? "sim" : "nao"}
            onClick={() => {
              setFiltro(opcao.chave);
              setPaginaAtual(0);
            }}
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
            {filtro || metodo || buscaAplicada || valorMinimo || valorMaximo
              ? "Nenhuma cobranca encontrada com esses filtros."
              : "Voce ainda nao criou nenhuma cobranca."}
          </p>
          <Link className="botao" href="/painel/nova">
            Criar a primeira
          </Link>
        </div>
      ) : (
        <>
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
          {totalDePaginas > 1 && (
            <nav className="paginacao" aria-label="Paginacao das cobrancas">
              <button
                className="botao"
                data-tom="vazado"
                type="button"
                disabled={paginaAtual === 0 || carregando}
                onClick={() => setPaginaAtual((atual) => Math.max(0, atual - 1))}
              >
                Anterior
              </button>
              <span>Pagina {paginaAtual + 1} de {totalDePaginas}</span>
              <button
                className="botao"
                data-tom="vazado"
                type="button"
                disabled={paginaAtual + 1 >= totalDePaginas || carregando}
                onClick={() => setPaginaAtual((atual) => atual + 1)}
              >
                Proxima
              </button>
            </nav>
          )}
        </>
      )}
    </>
  );
}
