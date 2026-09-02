export type StatusCobranca =
  | "AUTORIZADA"
  | "CAPTURADA"
  | "RECUSADA"
  | "CANCELADA"
  | "PARCIALMENTE_ESTORNADA"
  | "ESTORNADA";

export type MetodoPagamento = "CARTAO_CREDITO" | "CARTAO_DEBITO" | "PIX";

export type Bandeira = "VISA" | "MASTERCARD" | "ELO" | "AMEX" | "DESCONHECIDA";

export interface DadosCartao {
  bandeira: Bandeira;
  bin: string;
  ultimosQuatro: string;
  nomePortador?: string;
}

export interface Cobranca {
  codigo: string;
  valorEmCentavos: number;
  valorEstornadoEmCentavos: number;
  saldoEstornavelEmCentavos: number;
  moeda: string;
  descricao: string;
  metodo: MetodoPagamento;
  status: StatusCobranca;
  motivoRecusa?: string;
  descricaoDaRecusa?: string;
  codigoAutorizacao?: string;
  capturaAutomatica: boolean;
  cartao?: DadosCartao;
  criadoEm: string;
  atualizadoEm: string;
}

export interface Evento {
  tipo: string;
  statusAnterior?: StatusCobranca;
  statusNovo: StatusCobranca;
  detalhe?: string;
  criadoEm: string;
}

export interface Estorno {
  codigo: string;
  valorEmCentavos: number;
  motivo?: string;
  criadoEm: string;
}

export interface Pagina<T> {
  itens: T[];
  pagina: number;
  tamanho: number;
  totalDeItens: number;
  totalDePaginas: number;
}

export interface Estabelecimento {
  id: number;
  email: string;
  nomeEstabelecimento: string;
  criadoEm: string;
}

export interface Token {
  token: string;
  tipo: string;
  expiraEm: string;
}

/**
 * Erro no formato RFC 7807 devolvido pela API. Guarda o mapa de campos para
 * que o formulario consiga marcar exatamente o que foi reprovado.
 */
export class ErroDaApi extends Error {
  readonly status: number;
  readonly campos: Record<string, string>;

  constructor(mensagem: string, status: number, campos: Record<string, string> = {}) {
    super(mensagem);
    this.name = "ErroDaApi";
    this.status = status;
    this.campos = campos;
  }
}

interface Opcoes {
  metodo?: "GET" | "POST";
  corpo?: unknown;
  token?: string;
  chaveIdempotencia?: string;
}

async function requisicao<T>(caminho: string, opcoes: Opcoes = {}): Promise<T> {
  const cabecalhos: Record<string, string> = {};

  if (opcoes.corpo !== undefined) {
    cabecalhos["Content-Type"] = "application/json";
  }
  if (opcoes.token) {
    cabecalhos.Authorization = `Bearer ${opcoes.token}`;
  }
  if (opcoes.chaveIdempotencia) {
    cabecalhos["Idempotency-Key"] = opcoes.chaveIdempotencia;
  }

  let resposta: Response;
  try {
    resposta = await fetch(caminho, {
      method: opcoes.metodo ?? "GET",
      headers: cabecalhos,
      body: opcoes.corpo === undefined ? undefined : JSON.stringify(opcoes.corpo)
    });
  } catch {
    throw new ErroDaApi("Nao foi possivel falar com a API. Verifique se ela esta no ar.", 0);
  }

  const texto = await resposta.text();
  const dados = texto ? JSON.parse(texto) : null;

  if (!resposta.ok) {
    const detalhe: string = dados?.detail ?? dados?.title ?? `Erro ${resposta.status}`;
    throw new ErroDaApi(detalhe, resposta.status, dados?.campos ?? {});
  }

  return dados as T;
}

export const api = {
  registrar(corpo: { email: string; senha: string; nomeEstabelecimento: string }) {
    return requisicao<Estabelecimento>("/api/v1/autenticacao/registro", { metodo: "POST", corpo });
  },

  entrar(corpo: { email: string; senha: string }) {
    return requisicao<Token>("/api/v1/autenticacao/login", { metodo: "POST", corpo });
  },

  eu(token: string) {
    return requisicao<Estabelecimento>("/api/v1/autenticacao/eu", { token });
  },

  listarCobrancas(token: string, filtros: { status?: string; metodo?: string; tamanho?: number } = {}) {
    const busca = new URLSearchParams();
    if (filtros.status) busca.set("status", filtros.status);
    if (filtros.metodo) busca.set("metodo", filtros.metodo);
    busca.set("size", String(filtros.tamanho ?? 50));
    return requisicao<Pagina<Cobranca>>(`/api/v1/cobrancas?${busca}`, { token });
  },

  buscarCobranca(token: string, codigo: string) {
    return requisicao<Cobranca>(`/api/v1/cobrancas/${codigo}`, { token });
  },

  criarCobranca(token: string, corpo: unknown, chaveIdempotencia: string) {
    return requisicao<Cobranca>("/api/v1/cobrancas", {
      metodo: "POST",
      corpo,
      token,
      chaveIdempotencia
    });
  },

  capturar(token: string, codigo: string) {
    return requisicao<Cobranca>(`/api/v1/cobrancas/${codigo}/captura`, { metodo: "POST", token });
  },

  cancelar(token: string, codigo: string) {
    return requisicao<Cobranca>(`/api/v1/cobrancas/${codigo}/cancelamento`, { metodo: "POST", token });
  },

  estornar(token: string, codigo: string, corpo: { valorEmCentavos?: number; motivo?: string }) {
    return requisicao<Estorno>(`/api/v1/cobrancas/${codigo}/estornos`, {
      metodo: "POST",
      corpo,
      token
    });
  },

  eventos(token: string, codigo: string) {
    return requisicao<Evento[]>(`/api/v1/cobrancas/${codigo}/eventos`, { token });
  },

  estornos(token: string, codigo: string) {
    return requisicao<Estorno[]>(`/api/v1/cobrancas/${codigo}/estornos`, { token });
  }
};
