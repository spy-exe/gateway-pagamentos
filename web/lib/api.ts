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

export interface ResumoDoPeriodo {
  capturadoEmCentavos: number;
  estornadoEmCentavos: number;
  liquidoEmCentavos: number;
  autorizadoEmCentavos: number;
  recusadas: number;
  total: number;
  taxaDeAprovacao: number;
}

export interface DiaDoMovimento {
  dia: string;
  capturadoEmCentavos: number;
  transacoes: number;
  recusadas: number;
}

export interface FatiaDeBandeira {
  bandeira: Bandeira;
  transacoes: number;
  valorEmCentavos: number;
}

export interface EndpointWebhook {
  codigo: string;
  url: string;
  descricao?: string;
  ativo: boolean;
  segredo: string;
  criadoEm: string;
}

export type SituacaoDaEntrega = "PENDENTE" | "ENTREGUE" | "FALHOU";

export interface EntregaWebhook {
  codigo: string;
  evento: string;
  situacao: SituacaoDaEntrega;
  tentativas: number;
  ultimoCodigoHttp?: number;
  ultimaFalha?: string;
  proximaTentativaEm?: string;
  criadoEm: string;
  concluidoEm?: string;
  corpo: string;
}

export interface FiltroDeCobrancas {
  status?: string;
  metodo?: string;
  de?: string;
  ate?: string;
  busca?: string;
  tamanho?: number;
  pagina?: number;
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
  parcelas: number;
  valorDaParcelaEmCentavos: number;
  ajusteNaPrimeiraParcelaEmCentavos: number;
  pixCopiaECola?: string;
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
  metodo?: "GET" | "POST" | "DELETE";
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

function parametros(filtros: FiltroDeCobrancas): URLSearchParams {
  const busca = new URLSearchParams();
  if (filtros.status) busca.set("status", filtros.status);
  if (filtros.metodo) busca.set("metodo", filtros.metodo);
  if (filtros.de) busca.set("de", filtros.de);
  if (filtros.ate) busca.set("ate", filtros.ate);
  if (filtros.busca) busca.set("busca", filtros.busca);
  busca.set("size", String(filtros.tamanho ?? 50));
  busca.set("page", String(filtros.pagina ?? 0));
  return busca;
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

  listarCobrancas(token: string, filtros: FiltroDeCobrancas = {}) {
    return requisicao<Pagina<Cobranca>>(`/api/v1/cobrancas?${parametros(filtros)}`, { token });
  },

  resumo(token: string, dias = 30) {
    return requisicao<ResumoDoPeriodo>(`/api/v1/cobrancas/resumo?dias=${dias}`, { token });
  },

  movimento(token: string, dias = 30) {
    return requisicao<DiaDoMovimento[]>(`/api/v1/cobrancas/movimento?dias=${dias}`, { token });
  },

  bandeiras(token: string, dias = 30) {
    return requisicao<FatiaDeBandeira[]>(`/api/v1/cobrancas/bandeiras?dias=${dias}`, { token });
  },

  listarWebhooks(token: string) {
    return requisicao<EndpointWebhook[]>("/api/v1/webhooks", { token });
  },

  criarWebhook(token: string, corpo: { url: string; descricao?: string }) {
    return requisicao<EndpointWebhook>("/api/v1/webhooks", { metodo: "POST", corpo, token });
  },

  alternarWebhook(token: string, codigo: string, ativo: boolean) {
    return requisicao<EndpointWebhook>(`/api/v1/webhooks/${codigo}/situacao?ativo=${ativo}`, {
      metodo: "POST",
      token
    });
  },

  removerWebhook(token: string, codigo: string) {
    return requisicao<void>(`/api/v1/webhooks/${codigo}`, { metodo: "DELETE", token });
  },

  entregasDoWebhook(token: string, codigo: string) {
    return requisicao<Pagina<EntregaWebhook>>(`/api/v1/webhooks/${codigo}/entregas?size=20`, { token });
  },

  reenviarEntrega(token: string, codigo: string) {
    return requisicao<EntregaWebhook>(`/api/v1/webhooks/entregas/${codigo}/reenvio`, {
      metodo: "POST",
      token
    });
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
