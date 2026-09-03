"use client";

import type { FatiaDeBandeira } from "@/lib/api";
import { moeda, rotuloBandeira } from "@/lib/formato";
import { corDaBandeira } from "@/lib/paleta";

/*
  Participacao por bandeira. Barra deitada e nao pizza: comparar comprimento e
  facil, comparar angulo nao e. Cada barra carrega o proprio rotulo, entao a
  identidade nunca depende so da cor.
*/
export default function GraficoDeBandeiras({ fatias }: { fatias: FatiaDeBandeira[] }) {
  if (fatias.length === 0) {
    return <p className="grafico-vazio">Nenhuma transacao por cartao no periodo.</p>;
  }

  const teto = Math.max(...fatias.map((fatia) => fatia.valorEmCentavos), 1);

  return (
    <figure className="grafico">
      <figcaption className="grafico-titulo">
        <span className="etiqueta">Volume por bandeira</span>
      </figcaption>

      <ul className="barras">
        {fatias.map((fatia) => (
          <li key={fatia.bandeira} className="barra">
            <span className="barra-rotulo">{rotuloBandeira(fatia.bandeira)}</span>
            <span className="barra-trilho">
              <span
                className="barra-preenchida"
                style={{
                  width: `${Math.max((fatia.valorEmCentavos / teto) * 100, 1.5)}%`,
                  background: corDaBandeira(fatia.bandeira)
                }}
              />
            </span>
            <span className="barra-valor">{moeda(fatia.valorEmCentavos)}</span>
            <span className="barra-contagem">{fatia.transacoes}x</span>
          </li>
        ))}
      </ul>
    </figure>
  );
}
