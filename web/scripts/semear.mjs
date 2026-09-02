/*
  Popula a conta de demonstracao chamando a API de verdade, sem escrever no
  banco por fora. Cada cobranca aqui existe porque passou pelo autorizador,
  entao o painel mostra o mesmo que mostraria em uso normal.

  Uso: node scripts/semear.mjs [endereco]
*/
const ENDERECO = process.argv[2] ?? "http://localhost:8080";
const CONTA = {
  email: "demo@aval.app",
  senha: "demonstracao2026",
  nomeEstabelecimento: "Mercearia Sao Jorge"
};

const CARTOES = {
  visa: "4111111111111111",
  master: "5555555555554444",
  elo: "5099990000000003",
  amex: "378282246310005",
  bloqueado: "4111000000080000",
  semSaldo: "4111000000070001",
  semBandeira: "9999000000000004"
};

/** desfecho: capturada, autorizada, cancelada, estornoParcial, estornoTotal, recusada */
const ROTEIRO = [
  ["Compra do mes, caixa 2", 18790, "CARTAO_CREDITO", "visa", "capturada"],
  ["Feira da manha", 4360, "PIX", null, "capturada"],
  ["Padaria, conta da semana", 9820, "CARTAO_DEBITO", "master", "capturada"],
  ["Encomenda de bolo de aniversario", 30000, "CARTAO_CREDITO", "visa", "estornoParcial"],
  ["Cesta de frios", 12450, "CARTAO_CREDITO", "elo", "capturada"],
  ["Assinatura mensal do clube de vinhos", 8990, "CARTAO_CREDITO", "bloqueado", "recusada"],
  ["Compra do mes, caixa 1", 24310, "CARTAO_CREDITO", "master", "capturada"],
  ["Almoco executivo", 3890, "PIX", null, "capturada"],
  ["Fardo de agua mineral", 2790, "CARTAO_DEBITO", "visa", "capturada"],
  ["Reserva de peru de fim de ano", 21900, "CARTAO_CREDITO", "visa", "autorizada"],
  ["Cafe em grao, dois quilos", 7640, "CARTAO_CREDITO", "amex", "capturada"],
  ["Compra cancelada no caixa", 5320, "CARTAO_CREDITO", "master", "cancelada"],
  ["Queijo canastra meia peca", 13800, "CARTAO_CREDITO", "elo", "capturada"],
  ["Recarga de celular", 3000, "PIX", null, "capturada"],
  ["Compra do mes, caixa 3", 31240, "CARTAO_CREDITO", "semSaldo", "recusada"],
  ["Bandeja de ovos caipira", 2980, "CARTAO_DEBITO", "master", "capturada"],
  ["Carne para churrasco", 27650, "CARTAO_CREDITO", "visa", "estornoTotal"],
  ["Hortifruti, sacola grande", 6720, "PIX", null, "capturada"],
  ["Vinho tinto reserva", 15900, "CARTAO_CREDITO", "elo", "capturada"],
  ["Kit limpeza", 8410, "CARTAO_DEBITO", "visa", "capturada"],
  ["Pedido por telefone, entrega", 11230, "CARTAO_CREDITO", "master", "autorizada"],
  ["Compra teste do terminal novo", 100, "CARTAO_CREDITO", "visa", "estornoTotal"],
  ["Frios fatiados", 5480, "PIX", null, "capturada"],
  ["Panetone, encomenda", 9900, "CARTAO_CREDITO", "semBandeira", "recusada"],
  ["Racao para gato, saco de dez quilos", 18990, "CARTAO_CREDITO", "visa", "capturada"],
  ["Pao de queijo congelado", 4250, "CARTAO_DEBITO", "elo", "capturada"],
  ["Compra do mes, caixa 2", 20180, "CARTAO_CREDITO", "master", "estornoParcial"],
  ["Doceria, encomenda de festa", 45000, "CARTAO_CREDITO", "visa", "capturada"],
  ["Gas de cozinha", 12000, "PIX", null, "capturada"],
  ["Compra devolvida, produto vencido", 3670, "CARTAO_CREDITO", "visa", "estornoTotal"],
  ["Chocolate importado", 8890, "CARTAO_CREDITO", "amex", "capturada"],
  ["Sacola de padaria", 1990, "PIX", null, "capturada"],
  ["Cesta de natal corporativa", 128000, "CARTAO_CREDITO", "visa", "autorizada"],
  ["Compra do mes, caixa 1", 16740, "CARTAO_DEBITO", "master", "capturada"],
  ["Cerveja artesanal, caixa", 13600, "CARTAO_CREDITO", "elo", "estornoParcial"],
  ["Peixe fresco, encomenda de sexta", 9450, "CARTAO_CREDITO", "bloqueado", "recusada"]
];

async function chamar(caminho, { metodo = "GET", corpo, token, chave } = {}) {
  const cabecalhos = {};
  if (corpo !== undefined) cabecalhos["Content-Type"] = "application/json";
  if (token) cabecalhos.Authorization = `Bearer ${token}`;
  if (chave) cabecalhos["Idempotency-Key"] = chave;

  const resposta = await fetch(`${ENDERECO}${caminho}`, {
    method: metodo,
    headers: cabecalhos,
    body: corpo === undefined ? undefined : JSON.stringify(corpo)
  });

  const texto = await resposta.text();
  const dados = texto ? JSON.parse(texto) : null;
  if (!resposta.ok) {
    throw new Error(`${metodo} ${caminho} devolveu ${resposta.status}: ${dados?.detail ?? texto}`);
  }
  return dados;
}

async function principal() {
  try {
    await chamar("/api/v1/autenticacao/registro", { metodo: "POST", corpo: CONTA });
    console.log(`conta ${CONTA.email} criada`);
  } catch (erro) {
    if (!String(erro.message).includes("409")) throw erro;
    console.log(`conta ${CONTA.email} ja existia`);
  }

  const { token } = await chamar("/api/v1/autenticacao/login", {
    metodo: "POST",
    corpo: { email: CONTA.email, senha: CONTA.senha }
  });

  const jaExistem = await chamar("/api/v1/cobrancas?size=1", { token });
  if (jaExistem.totalDeItens >= ROTEIRO.length) {
    console.log(`conta ja tem ${jaExistem.totalDeItens} cobrancas, nada a fazer`);
    return;
  }

  let criadas = 0;

  for (const [descricao, valor, metodo, cartao, desfecho] of ROTEIRO) {
    const capturaAutomatica = desfecho === "capturada" || desfecho === "recusada";

    const cobranca = await chamar("/api/v1/cobrancas", {
      metodo: "POST",
      token,
      chave: `demo-${criadas}-${descricao.slice(0, 18)}`,
      corpo: {
        valorEmCentavos: valor,
        descricao,
        metodo,
        capturaAutomatica,
        cartao: cartao
          ? {
              numero: CARTOES[cartao],
              validadeMes: 12,
              validadeAno: 2030,
              nomePortador: "Ricardo Figueiredo"
            }
          : undefined
      }
    });

    if (desfecho === "cancelada") {
      await chamar(`/api/v1/cobrancas/${cobranca.codigo}/cancelamento`, { metodo: "POST", token });
    }

    if (desfecho === "estornoParcial" || desfecho === "estornoTotal") {
      await chamar(`/api/v1/cobrancas/${cobranca.codigo}/captura`, { metodo: "POST", token });
      await chamar(`/api/v1/cobrancas/${cobranca.codigo}/estornos`, {
        metodo: "POST",
        token,
        corpo:
          desfecho === "estornoParcial"
            ? { valorEmCentavos: Math.round(valor * 0.4), motivo: "item devolvido pelo cliente" }
            : { motivo: "compra cancelada apos a captura" }
      });
    }

    criadas += 1;
  }

  const resumo = await chamar("/api/v1/cobrancas?size=1", { token });
  console.log(`${criadas} cobrancas criadas, total na conta: ${resumo.totalDeItens}`);
}

principal().catch((erro) => {
  console.error(erro.message);
  process.exit(1);
});
