import type { Cobranca } from "./api";

export interface Fechamento {
  capturado: number;
  estornado: number;
  liquido: number;
  recusadas: number;
}

/**
 * Fechamento de caixa da listagem: o que de fato virou dinheiro, o que voltou
 * ao portador e o que sobrou. Cobranca apenas autorizada nao entra, porque o
 * valor ainda esta reservado e pode ser cancelado.
 */
export function calcularFechamento(cobrancas: Cobranca[]): Fechamento {
  const capturado = cobrancas
    .filter(
      (cobranca) =>
        cobranca.status !== "RECUSADA" &&
        cobranca.status !== "CANCELADA" &&
        cobranca.status !== "AUTORIZADA"
    )
    .reduce((total, cobranca) => total + cobranca.valorEmCentavos, 0);

  const estornado = cobrancas.reduce((total, cobranca) => total + cobranca.valorEstornadoEmCentavos, 0);
  const recusadas = cobrancas.filter((cobranca) => cobranca.status === "RECUSADA").length;

  return { capturado, estornado, liquido: capturado - estornado, recusadas };
}
