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

  await semearWebhook(token);
  await semearParceladasEPix(token);
  await semearLinks(token);

  const jaExistem = await chamar("/api/v1/cobrancas?size=1", { token });
  if (jaExistem.totalDeItens >= ROTEIRO.length) {
    console.log(`conta ja tem ${jaExistem.totalDeItens} cobrancas, roteiro base ja aplicado`);
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

/** Vitrine publica: links ativos, esgotado e pausado, com cobrancas de origem rastreada. */
async function semearLinks(token) {
  const existentes = await chamar("/api/v1/links-pagamento", { token });
  if (existentes.length > 0) {
    console.log(`links de pagamento ja cadastrados (${existentes.length})`);
    return;
  }

  const validade = new Date();
  validade.setDate(validade.getDate() + 90);

  const cesta = await chamar("/api/v1/links-pagamento", {
    metodo: "POST",
    token,
    corpo: {
      descricao: "Cesta especial da Mercearia Sao Jorge",
      valorEmCentavos: 12900,
      metodo: "PIX",
      limiteDeUsos: 12,
      expiraEm: validade.toISOString()
    }
  });

  const clube = await chamar("/api/v1/links-pagamento", {
    metodo: "POST",
    token,
    corpo: {
      descricao: "Clube do cafe, assinatura trimestral",
      valorEmCentavos: 20970,
      metodo: "CARTAO_CREDITO",
      parcelasMaximas: 3
    }
  });

  const esgotado = await chamar("/api/v1/links-pagamento", {
    metodo: "POST",
    token,
    corpo: {
      descricao: "Lote relampago de cafe especial",
      valorEmCentavos: 4590,
      metodo: "PIX",
      limiteDeUsos: 1
    }
  });

  const pausado = await chamar("/api/v1/links-pagamento", {
    metodo: "POST",
    token,
    corpo: {
      descricao: "Encomenda sazonal encerrada",
      valorEmCentavos: 8990,
      metodo: "CARTAO_DEBITO"
    }
  });

  await chamar(`/api/v1/links-pagamento/publicos/${cesta.codigo}/finalizacao`, {
    metodo: "POST",
    chave: "demo-link-cesta-primeiro-pedido",
    corpo: {}
  });
  await chamar(`/api/v1/links-pagamento/publicos/${clube.codigo}/finalizacao`, {
    metodo: "POST",
    chave: "demo-link-clube-primeira-assinatura",
    corpo: {
      parcelas: 3,
      cartao: {
        numero: CARTOES.visa,
        validadeMes: 12,
        validadeAno: 2030,
        nomePortador: "Cliente da vitrine"
      }
    }
  });
  await chamar(`/api/v1/links-pagamento/publicos/${esgotado.codigo}/finalizacao`, {
    metodo: "POST",
    chave: "demo-link-lote-esgotado",
    corpo: {}
  });
  await chamar(`/api/v1/links-pagamento/${pausado.codigo}/situacao?ativo=false`, {
    metodo: "POST",
    token
  });

  console.log("4 links de pagamento criados, com exemplos ativo, esgotado e pausado");
}

/** Um endpoint apontando para o eco publico, para as entregas aparecerem concluidas. */
async function semearWebhook(token) {
  const existentes = await chamar("/api/v1/webhooks", { token });
  if (existentes.length > 0) {
    console.log(`webhook ja cadastrado (${existentes.length})`);
    return;
  }

  const destino = process.env.WEBHOOK_DEMO ?? "https://api-gateway-aula.malha.app/api/v1/webhooks/eco";
  const criado = await chamar("/api/v1/webhooks", {
    metodo: "POST",
    token,
    corpo: { url: destino, descricao: "eco de demonstracao" }
  });

  console.log(`webhook ${criado.codigo} apontando para ${destino}`);
}

/** Segunda leva: parcelamento e Pix, que so existem a partir da migracao V2. */
async function semearParceladasEPix(token) {
  const parceladas = [
    ["Geladeira, entrada da loja", 249900, 12],
    ["Fogao seis bocas", 189000, 10],
    ["Jogo de panelas", 45900, 6],
    ["Cafeteira expresso", 89900, 8],
    ["Air fryer", 39900, 3]
  ];

  const pix = [
    ["Marmita do dia", 2200],
    ["Feira da tarde", 8750],
    ["Bolo de pote", 1500],
    ["Cesta de cafe da manha", 12900]
  ];

  const jaTem = await chamar("/api/v1/cobrancas?size=100", { token });
  if (jaTem.itens.some((cobranca) => cobranca.parcelas > 1)) {
    console.log("segunda leva ja aplicada");
    return;
  }

  for (const [descricao, valor, parcelas] of parceladas) {
    await chamar("/api/v1/cobrancas", {
      metodo: "POST",
      token,
      chave: `demo-parcelada-${descricao.slice(0, 14)}`,
      corpo: {
        valorEmCentavos: valor,
        descricao,
        metodo: "CARTAO_CREDITO",
        parcelas,
        cartao: {
          numero: CARTOES.visa,
          validadeMes: 12,
          validadeAno: 2030,
          nomePortador: "Ricardo Figueiredo"
        }
      }
    });
  }

  for (const [descricao, valor] of pix) {
    await chamar("/api/v1/cobrancas", {
      metodo: "POST",
      token,
      chave: `demo-pix-${descricao.slice(0, 16)}`,
      corpo: { valorEmCentavos: valor, descricao, metodo: "PIX" }
    });
  }

  console.log(`${parceladas.length} parceladas e ${pix.length} cobrancas Pix criadas`);
}

principal().catch((erro) => {
  console.error(erro.message);
  process.exit(1);
});
