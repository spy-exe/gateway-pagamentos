import type { Bandeira, MetodoPagamento, StatusCobranca } from "./api";

const REAIS = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });

export function moeda(centavos: number): string {
  return REAIS.format(centavos / 100);
}

export function dataHora(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function dataCurta(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", {
    day: "2-digit",
    month: "2-digit",
    hour: "2-digit",
    minute: "2-digit"
  });
}

export function horario(iso: string): string {
  return new Date(iso).toLocaleTimeString("pt-BR", { hour: "2-digit", minute: "2-digit", second: "2-digit" });
}

const METODOS: Record<MetodoPagamento, string> = {
  CARTAO_CREDITO: "Credito",
  CARTAO_DEBITO: "Debito",
  PIX: "Pix"
};

export function rotuloMetodo(metodo: MetodoPagamento): string {
  return METODOS[metodo] ?? metodo;
}

const STATUS: Record<StatusCobranca, string> = {
  AUTORIZADA: "Autorizada",
  CAPTURADA: "Capturada",
  RECUSADA: "Recusada",
  CANCELADA: "Cancelada",
  PARCIALMENTE_ESTORNADA: "Estorno parcial",
  ESTORNADA: "Estornada"
};

export function rotuloStatus(status: StatusCobranca): string {
  return STATUS[status] ?? status;
}

const EVENTOS: Record<string, string> = {
  AUTORIZACAO: "Autorizacao",
  RECUSA: "Recusa",
  CAPTURA: "Captura",
  CANCELAMENTO: "Cancelamento",
  ESTORNO: "Estorno"
};

export function rotuloEvento(tipo: string): string {
  return EVENTOS[tipo] ?? tipo;
}

const BANDEIRAS: Record<Bandeira, string> = {
  VISA: "Visa",
  MASTERCARD: "Mastercard",
  ELO: "Elo",
  AMEX: "Amex",
  DESCONHECIDA: "Bandeira nao identificada"
};

export function rotuloBandeira(bandeira: Bandeira): string {
  return BANDEIRAS[bandeira] ?? bandeira;
}

/** Repete a deteccao de bandeira do backend para dar retorno enquanto se digita. */
export function bandeiraDoNumero(numero: string): Bandeira {
  const digitos = numero.replace(/\D/g, "");
  if (digitos.length < 4) return "DESCONHECIDA";

  const prefixosElo = [
    "401178", "401179", "431274", "438935", "451416", "457393",
    "504175", "506699", "509", "627780", "636297", "636368", "650", "6516", "6550"
  ];

  if (prefixosElo.some((prefixo) => digitos.startsWith(prefixo))) return "ELO";
  if (digitos.startsWith("34") || digitos.startsWith("37")) return "AMEX";
  if (digitos.startsWith("4")) return "VISA";

  const dois = Number(digitos.slice(0, 2));
  const quatro = Number(digitos.slice(0, 4));
  if ((dois >= 51 && dois <= 55) || (quatro >= 2221 && quatro <= 2720)) return "MASTERCARD";

  return "DESCONHECIDA";
}

export function agruparNumero(numero: string): string {
  const digitos = numero.replace(/\D/g, "").slice(0, 19);
  return digitos.replace(/(.{4})/g, "$1 ").trim();
}

export function centavosDeTexto(texto: string): number | null {
  const limpo = texto.replace(/[^\d,.-]/g, "").replace(/\./g, "").replace(",", ".");
  if (!limpo) return null;
  const valor = Number(limpo);
  if (!Number.isFinite(valor)) return null;
  return Math.round(valor * 100);
}
