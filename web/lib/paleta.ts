/**
 * Paleta dos graficos.
 *
 * A ordem e fixa e a cor segue a entidade, nunca a posicao no ranking: se um
 * filtro tirar a Elo da lista, a Visa continua verde. Os cinco tons passaram
 * na conferencia de banda de luminosidade, piso de croma, separacao sob
 * daltonismo protan, deutan e tritan, e contraste contra o papel do painel.
 */
export const CORES_DE_BANDEIRA: Record<string, string> = {
  VISA: "#0a8f74",
  MASTERCARD: "#a8761c",
  ELO: "#3a6ea8",
  AMEX: "#b0455c",
  DESCONHECIDA: "#6b5aa8"
};

export const COR_PADRAO_DE_SERIE = "#6b5aa8";

/** Serie unica usa a tinta da marca: sem competicao de identidade, sem legenda. */
export const COR_DA_SERIE_UNICA = "#0e4a43";

export function corDaBandeira(bandeira: string): string {
  return CORES_DE_BANDEIRA[bandeira] ?? COR_PADRAO_DE_SERIE;
}
