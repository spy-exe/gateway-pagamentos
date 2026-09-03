"use client";

import { useId, useState } from "react";
import type { DiaDoMovimento } from "@/lib/api";
import { moeda } from "@/lib/formato";
import { COR_DA_SERIE_UNICA } from "@/lib/paleta";

/*
  Volume capturado por dia. Serie unica, entao sem legenda: o titulo ja diz o
  que a barra mede. Rotulo direto so no maior dia, porque numero em cima de
  toda barra vira ruido e some justamente o que importa.
*/

const ALTURA = 168;
const MARGEM_INFERIOR = 22;
const MARGEM_SUPERIOR = 14;

export default function GraficoDeVolume({ dias }: { dias: DiaDoMovimento[] }) {
  const identificador = useId();
  const [emFoco, setEmFoco] = useState<number | null>(null);

  if (dias.length === 0) {
    return <p className="grafico-vazio">Sem movimento no periodo.</p>;
  }

  const teto = Math.max(...dias.map((dia) => dia.capturadoEmCentavos), 1);
  const indiceDoMaior = dias.findIndex((dia) => dia.capturadoEmCentavos === teto);
  const larguraDaFaixa = 100 / dias.length;
  const areaUtil = ALTURA - MARGEM_INFERIOR - MARGEM_SUPERIOR;

  const linhasDeApoio = [0.5, 1].map((fracao) => ({
    fracao,
    y: MARGEM_SUPERIOR + areaUtil * (1 - fracao),
    valor: teto * fracao
  }));

  const foco = emFoco === null ? null : dias[emFoco];

  return (
    <figure className="grafico">
      <figcaption className="grafico-titulo">
        <span className="etiqueta">Capturado por dia</span>
        <span className="grafico-leitura">
          {foco
            ? `${formatarDia(foco.dia)} · ${moeda(foco.capturadoEmCentavos)} · ${foco.transacoes} transacao(oes)`
            : `Maior dia: ${moeda(teto)}`}
        </span>
      </figcaption>

      <svg
        viewBox={`0 0 100 ${ALTURA}`}
        preserveAspectRatio="none"
        role="img"
        aria-labelledby={`${identificador}-titulo`}
        className="grafico-tela"
        onMouseLeave={() => setEmFoco(null)}
      >
        <title id={`${identificador}-titulo`}>
          Volume capturado por dia nos ultimos {dias.length} dias
        </title>

        {linhasDeApoio.map((linha) => (
          <line
            key={linha.fracao}
            x1="0"
            x2="100"
            y1={linha.y}
            y2={linha.y}
            className="grafico-apoio"
            vectorEffect="non-scaling-stroke"
          />
        ))}

        <line
          x1="0"
          x2="100"
          y1={ALTURA - MARGEM_INFERIOR}
          y2={ALTURA - MARGEM_INFERIOR}
          className="grafico-base"
          vectorEffect="non-scaling-stroke"
        />

        {dias.map((dia, indice) => {
          const altura = (dia.capturadoEmCentavos / teto) * areaUtil;
          const x = indice * larguraDaFaixa;
          const largura = larguraDaFaixa;

          return (
            <g key={dia.dia} onMouseEnter={() => setEmFoco(indice)}>
              {/* alvo de mouse maior que a barra, para nao exigir pontaria */}
              <rect x={x} y={0} width={largura} height={ALTURA} fill="transparent" />
              <rect
                x={x + largura * 0.18}
                y={ALTURA - MARGEM_INFERIOR - Math.max(altura, dia.capturadoEmCentavos > 0 ? 2 : 0)}
                width={largura * 0.64}
                height={Math.max(altura, dia.capturadoEmCentavos > 0 ? 2 : 0)}
                fill={COR_DA_SERIE_UNICA}
                opacity={emFoco === null || emFoco === indice ? 1 : 0.32}
                rx="0.6"
              />
            </g>
          );
        })}
      </svg>

      <div className="grafico-eixo">
        <span>{formatarDia(dias[0].dia)}</span>
        {dias.length > 2 && <span>{formatarDia(dias[Math.floor(dias.length / 2)].dia)}</span>}
        <span>{formatarDia(dias[dias.length - 1].dia)}</span>
      </div>

      <table className="apenas-leitor-de-tela">
        <caption>Volume capturado por dia</caption>
        <thead>
          <tr>
            <th scope="col">Dia</th>
            <th scope="col">Capturado</th>
            <th scope="col">Transacoes</th>
          </tr>
        </thead>
        <tbody>
          {dias.map((dia) => (
            <tr key={dia.dia}>
              <th scope="row">{formatarDia(dia.dia)}</th>
              <td>{moeda(dia.capturadoEmCentavos)}</td>
              <td>{dia.transacoes}</td>
            </tr>
          ))}
        </tbody>
      </table>

      <span className="apenas-leitor-de-tela">Maior dia: {formatarDia(dias[indiceDoMaior].dia)}</span>
    </figure>
  );
}

function formatarDia(dia: string): string {
  const [ano, mes, diaDoMes] = dia.split("-");
  return `${diaDoMes}/${mes}`.concat(ano ? "" : "");
}
