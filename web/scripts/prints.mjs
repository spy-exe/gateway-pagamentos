/*
  Percorre o painel em um Chromium headless e grava as telas em prints/.
  Serve tanto para conferir o visual sem abrir o navegador quanto para provar
  que o fluxo inteiro funciona ponta a ponta contra a API de verdade.

  Uso: node scripts/prints.mjs [endereco]
*/
import { mkdir, rm } from "node:fs/promises";
import puppeteer from "puppeteer-core";

const ENDERECO = process.argv[2] ?? "http://localhost:8080";
const PASTA = new URL("../prints/", import.meta.url).pathname;
const SENHA = "senhaforte123";
const email = `vitrine+${Date.now()}@exemplo.com`;

const espera = (ms) => new Promise((resolve) => setTimeout(resolve, ms));

async function principal() {
  await rm(PASTA, { recursive: true, force: true });
  await mkdir(PASTA, { recursive: true });

  const navegador = await puppeteer.launch({
    executablePath: process.env.CHROMIUM ?? "/usr/bin/chromium",
    headless: true,
    args: [
      "--no-sandbox",
      "--disable-dev-shm-usage",
      "--enable-unsafe-swiftshader",
      "--use-gl=angle",
      "--use-angle=swiftshader",
      "--force-device-scale-factor=2"
    ]
  });

  const pagina = await navegador.newPage();
  await pagina.setViewport({ width: 1440, height: 900, deviceScaleFactor: 2 });

  const registro = [];
  pagina.on("console", (msg) => {
    if (msg.type() === "error") registro.push(msg.text());
  });
  pagina.on("pageerror", (erro) => registro.push(String(erro)));

  async function print(nome) {
    await espera(700);
    await pagina.screenshot({ path: `${PASTA}${nome}.png` });
    console.log(`gravado ${nome}.png`);
  }

  // 1. entrada, com o cartao girando
  await pagina.goto(ENDERECO, { waitUntil: "networkidle0" });
  await espera(2200); // deixa a foil chegar num angulo interessante
  await print("01-entrada");

  // 2. criar conta
  await pagina.click('button[role="tab"]:nth-of-type(2)');
  await pagina.type('input[autocomplete="organization"]', "Padaria do Ricardo");
  await pagina.type('input[type="email"]', email);
  await pagina.type('input[type="password"]', SENHA);
  await print("02-criar-conta");

  await Promise.all([
    pagina.waitForNavigation({ waitUntil: "networkidle0" }),
    pagina.click('button[type="submit"]')
  ]);
  await print("03-painel-vazio");

  // 3. formulario com o cartao reagindo ao que foi digitado
  await pagina.goto(`${ENDERECO}/painel/nova`, { waitUntil: "networkidle0" });
  await pagina.click('input[placeholder="R$ 0,00"]');
  await pagina.type('input[placeholder="R$ 0,00"]', "30000");
  await pagina.type('input[placeholder="Encomenda de bolo"]', "Encomenda de bolo de festa");
  await pagina.type('input[placeholder="4111 1111 1111 1111"]', "4111111111111111");
  await pagina.type('input[placeholder="Ricardo Figueiredo"]', "Ricardo Figueiredo");
  await pagina.click('input[type="checkbox"]'); // captura manual, para exibir o passo seguinte
  await espera(1200);
  await print("04-nova-cobranca");

  await Promise.all([
    pagina.waitForNavigation({ waitUntil: "networkidle0" }),
    pagina.click('button[type="submit"]')
  ]);
  await print("05-comprovante-autorizada");

  // 4. captura e estorno parcial
  await pagina.evaluate(() => {
    const botao = [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Capturar");
    botao?.click();
  });
  await espera(1400);
  await print("06-comprovante-capturada");

  await pagina.type('input[placeholder="tudo"]', "10000");
  await pagina.evaluate(() => {
    const botao = [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Estornar");
    botao?.click();
  });
  await espera(1600);
  await print("07-comprovante-estorno-parcial");

  // 5. uma recusa, para a lista ter variedade
  await pagina.goto(`${ENDERECO}/painel/nova`, { waitUntil: "networkidle0" });
  await pagina.type('input[placeholder="R$ 0,00"]', "8990");
  await pagina.type('input[placeholder="Encomenda de bolo"]', "Assinatura mensal");
  await pagina.type('input[placeholder="4111 1111 1111 1111"]', "4111000000080000");
  await pagina.type('input[placeholder="Ricardo Figueiredo"]', "Ricardo Figueiredo");
  await Promise.all([
    pagina.waitForNavigation({ waitUntil: "networkidle0" }),
    pagina.click('button[type="submit"]')
  ]);
  await print("08-comprovante-recusada");

  // 6. um Pix, aprovado na hora
  await pagina.goto(`${ENDERECO}/painel/nova`, { waitUntil: "networkidle0" });
  await pagina.type('input[placeholder="R$ 0,00"]', "4990");
  await pagina.type('input[placeholder="Encomenda de bolo"]', "Cafe e pao na chapa");
  await pagina.select("select", "PIX");
  await Promise.all([
    pagina.waitForNavigation({ waitUntil: "networkidle0" }),
    pagina.click('button[type="submit"]')
  ]);

  // 7. a lista cheia
  await pagina.goto(`${ENDERECO}/painel`, { waitUntil: "networkidle0" });
  await print("09-razao");

  // 8. telas estreitas
  await pagina.setViewport({ width: 390, height: 844, deviceScaleFactor: 2 });
  await pagina.goto(`${ENDERECO}/painel`, { waitUntil: "networkidle0" });
  await print("10-razao-celular");

  // sem limpar a sessao a entrada redireciona direto para o painel
  await pagina.evaluate(() => window.localStorage.clear());
  await pagina.goto(ENDERECO, { waitUntil: "networkidle0" });
  await espera(2400);
  await print("11-entrada-celular");

  // 9. a conta de demonstracao, com o extrato cheio
  await pagina.setViewport({ width: 1440, height: 1000, deviceScaleFactor: 2 });
  await pagina.evaluate(() => window.localStorage.clear());
  await pagina.goto(ENDERECO, { waitUntil: "networkidle0" });
  await pagina.type('input[type="email"]', "demo@aval.app");
  await pagina.type('input[type="password"]', "demonstracao2026");
  await Promise.all([
    pagina.waitForNavigation({ waitUntil: "networkidle0" }),
    pagina.click('button[type="submit"]')
  ]);
  await print("12-demo-razao");

  await pagina.evaluate(() => {
    const linha = document.querySelectorAll(".razao-linha")[3];
    linha?.dispatchEvent(new MouseEvent("click", { bubbles: true }));
  });
  await espera(1800);
  await print("13-demo-comprovante");

  // 10. um comprovante de Pix, com o QR desenhado a partir do copia e cola
  await pagina.goto(`${ENDERECO}/painel?`, { waitUntil: "networkidle0" });
  const codigoPix = await pagina.evaluate(() => {
    const linhas = [...document.querySelectorAll(".razao-linha")];
    const alvo = linhas.find((linha) => linha.textContent.includes("Pix"));
    return alvo ? alvo.getAttribute("href") : null;
  });
  if (codigoPix) {
    await pagina.goto(`${ENDERECO}${codigoPix}`, { waitUntil: "networkidle0" });
    await espera(1600);
    await print("14-comprovante-pix");
  }

  // 11. a tela de webhooks, com as entregas abertas
  await pagina.goto(`${ENDERECO}/painel/webhooks`, { waitUntil: "networkidle0" });
  await espera(900);
  await pagina.evaluate(() => {
    const botao = [...document.querySelectorAll("button")].find((b) => b.textContent.trim() === "Ver entregas");
    botao?.click();
  });
  await espera(1600);
  await print("15-webhooks");

  // 12. links de pagamento e o checkout publico da conta demo
  await pagina.goto(`${ENDERECO}/painel/links`, { waitUntil: "networkidle0" });
  await pagina.evaluate(() => window.scrollTo(0, 0));
  await print("16-links-de-pagamento");

  const linkPublico = await pagina.evaluate(() => {
    const cartaoAtivo = [...document.querySelectorAll(".link-cartao")]
      .find((item) => item.textContent.toLowerCase().includes("aceitando pagamentos"));
    const ancora = cartaoAtivo?.querySelector('a[href^="/pagar/"]');
    return ancora?.getAttribute("href") ?? null;
  });
  if (linkPublico) {
    await pagina.evaluate(() => window.localStorage.clear());
    await pagina.goto(`${ENDERECO}${linkPublico}`, { waitUntil: "networkidle0" });
    await print("17-checkout-publico");
  }

  await navegador.close();

  if (registro.length > 0) {
    console.log("\nerros no console do navegador:");
    registro.forEach((linha) => console.log(`  ${linha}`));
    process.exitCode = 1;
  } else {
    console.log("\nnenhum erro no console do navegador");
  }
}

principal().catch((erro) => {
  console.error(erro);
  process.exit(1);
});
