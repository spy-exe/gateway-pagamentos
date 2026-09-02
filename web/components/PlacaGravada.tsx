"use client";

import { useEffect, useRef } from "react";
import * as THREE from "three";
import type { Bandeira } from "@/lib/api";
import { rotuloBandeira } from "@/lib/formato";

/*
  A unica peca em tres dimensoes do painel e o cartao, tratado como o metal
  gravado que ele e num banco: aco escovado com guilhoche, aquele desenho de
  linhas concentricas que cedula, cheque e apolice usam desde o seculo XIX
  justamente porque e dificil de falsificar.

  O padrao e calculado no shader, curva por curva, e nao desenhado em imagem.
  A face impressa vem de um canvas 2D virado textura, entao numero, bandeira e
  nome mudam junto com o formulario.
*/

const PROPORCAO = 1.586; // ID-1, a mesma de um cartao de verdade

const VERTICE = /* glsl */ `
  varying vec2 vUv;
  varying vec3 vNormalMundo;
  varying vec3 vVisao;

  void main() {
    vUv = uv;
    vec4 mundo = modelMatrix * vec4(position, 1.0);
    vNormalMundo = normalize(mat3(modelMatrix) * normal);
    vVisao = normalize(cameraPosition - mundo.xyz);
    gl_Position = projectionMatrix * viewMatrix * mundo;
  }
`;

const FRAGMENTO = /* glsl */ `
  precision highp float;

  uniform sampler2D uFace;
  uniform sampler2D uVerso;
  uniform float uTempo;
  uniform vec3 uAcoBaixo;
  uniform vec3 uAcoAlto;
  uniform vec3 uLaton;
  uniform float uBrilho;

  varying vec2 vUv;
  varying vec3 vNormalMundo;
  varying vec3 vVisao;

  float bordaArredondada(vec2 ponto, vec2 meia, float raio) {
    vec2 q = abs(ponto) - (meia - raio);
    return length(max(q, 0.0)) + min(max(q.x, q.y), 0.0) - raio;
  }

  float ruido(float semente) {
    return fract(sin(semente * 91.3458) * 47453.5453);
  }

  // uma linha do buril: fina, com espessura constante em tela
  float linhaGravada(float onda, float peso) {
    float distancia = abs(fract(onda) - 0.5) * 2.0;
    float largura = fwidth(onda) * 2.4 + 0.02;
    return 1.0 - smoothstep(peso - largura, peso + largura, distancia);
  }

  // roseta de guilhoche: raio modulado por harmonicas do angulo
  float roseta(vec2 ponto, float bracos, float amplitude, float densidade, float giro) {
    float angulo = atan(ponto.y, ponto.x) + giro;
    float raio = length(ponto);
    float onda = raio * densidade
      + sin(angulo * bracos) * amplitude
      + cos(angulo * bracos * 0.6) * amplitude * 0.4;
    float desenho = linhaGravada(onda, 0.26);
    // a gravacao morre bem antes de encostar na vizinha, senao vira moire
    return desenho * (1.0 - smoothstep(0.10, 0.33, raio));
  }

  // fio de seguranca correndo rente a borda, como em apolice
  float fioDaBorda(vec2 ponto) {
    float d = abs(bordaArredondada(ponto, vec2(${(PROPORCAO / 2).toFixed(3)}, 0.5), 0.072));
    return (1.0 - smoothstep(0.0, 0.004, abs(d - 0.030)))
         + (1.0 - smoothstep(0.0, 0.004, abs(d - 0.044))) * 0.6;
  }

  float guilloche(vec2 ponto) {
    float desenho = 0.0;
    desenho = max(desenho, roseta(ponto - vec2(0.50, 0.02), 11.0, 0.30, 34.0, 0.0));
    desenho = max(desenho, roseta(ponto + vec2(0.52, 0.10), 9.0, 0.26, 30.0, 1.1));
    desenho = max(desenho, fioDaBorda(ponto));
    return desenho;
  }

  void main() {
    vec2 ponto = (vUv - 0.5) * vec2(${PROPORCAO.toFixed(3)}, 1.0);
    float distancia = bordaArredondada(ponto, vec2(${(PROPORCAO / 2).toFixed(3)}, 0.5), 0.072);
    float recorte = 1.0 - smoothstep(-0.004, 0.004, distancia);
    if (recorte <= 0.001) discard;

    vec3 normal = normalize(vNormalMundo);
    vec3 visao = normalize(vVisao);
    float rasante = pow(1.0 - abs(dot(normal, visao)), 2.6);

    // aco escovado: gradiente vertical com estrias horizontais finas
    vec3 metal = mix(uAcoBaixo, uAcoAlto, smoothstep(0.0, 1.0, vUv.y));
    metal += (ruido(floor(vUv.y * 620.0)) - 0.5) * 0.022;

    // varredura especular, morna, sem cor de arco-iris
    float eixo = vUv.x * ${PROPORCAO.toFixed(3)} + vUv.y * 0.55;
    float varredura = pow(0.5 + 0.5 * sin(eixo * 2.1 - uTempo * 0.42), 9.0);

    vec3 cor;
    if (gl_FrontFacing) {
      cor = mix(metal, uLaton, guilloche(ponto) * 0.24);
      cor += vec3(0.98, 0.93, 0.82) * varredura * (0.10 + 0.20 * rasante);
      cor += uLaton * rasante * 0.12;

      vec4 impresso = texture2D(uFace, vUv);
      cor = mix(cor, impresso.rgb * (0.92 + varredura * 0.5), impresso.a);
    } else {
      // visto por tras, o plano espelha em x; a textura do verso compensa
      vec2 uvVerso = vec2(1.0 - vUv.x, vUv.y);
      cor = metal * 0.82;
      cor = mix(cor, uLaton, guilloche(ponto) * 0.13);
      cor += vec3(0.98, 0.93, 0.82) * varredura * 0.07;

      vec4 impressoVerso = texture2D(uVerso, uvVerso);
      cor = mix(cor, impressoVerso.rgb * (0.95 + varredura * 0.35), impressoVerso.a);
    }

    gl_FragColor = vec4(cor * uBrilho, recorte);
  }
`;

interface Props {
  bandeira?: Bandeira;
  ultimosQuatro?: string;
  numeroParcial?: string;
  nomePortador?: string;
  validade?: string;
  /** palco gira a peca por inteiro; bancada mantem a face legivel para leitura */
  modo?: "palco" | "bancada";
  altura?: number | string;
}

function desenharFace(
  canvas: HTMLCanvasElement,
  dados: { bandeira: Bandeira; numeroParcial: string; nomePortador: string; validade: string },
  fonteMono: string,
  fonteDisplay: string
) {
  const contexto = canvas.getContext("2d");
  if (!contexto) return;

  const largura = canvas.width;
  const altura = canvas.height;
  contexto.clearRect(0, 0, largura, altura);
  contexto.textBaseline = "alphabetic";

  const claro = "rgba(238, 232, 218, 0.94)";
  const meio = "rgba(226, 214, 188, 0.66)";
  const fraco = "rgba(214, 202, 178, 0.40)";

  // logotipo do emissor, na mesma didone do resto da marca
  contexto.fillStyle = claro;
  contexto.font = `600 ${largura * 0.052}px ${fonteDisplay}`;
  contexto.letterSpacing = `${largura * 0.017}px`;
  contexto.fillText("AVAL", largura * 0.075, altura * 0.175);

  contexto.strokeStyle = "rgba(198, 156, 82, 0.5)";
  contexto.lineWidth = Math.max(1, largura * 0.0012);
  contexto.beginPath();
  contexto.moveTo(largura * 0.075, altura * 0.215);
  contexto.lineTo(largura * 0.30, altura * 0.215);
  contexto.stroke();

  contexto.fillStyle = fraco;
  contexto.font = `400 ${largura * 0.0175}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.005}px`;
  contexto.fillText("INSTITUICAO DE PAGAMENTO", largura * 0.075, altura * 0.262);

  // bandeira
  contexto.fillStyle = meio;
  contexto.font = `500 ${largura * 0.030}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.008}px`;
  const nomeBandeira = dados.bandeira === "DESCONHECIDA" ? "" : rotuloBandeira(dados.bandeira).toUpperCase();
  contexto.fillText(nomeBandeira, largura - largura * 0.075 - contexto.measureText(nomeBandeira).width, altura * 0.175);

  // chip, em latao fosco
  const chipX = largura * 0.075;
  const chipY = altura * 0.33;
  const chipL = largura * 0.10;
  const chipA = chipL * 0.76;
  contexto.beginPath();
  contexto.roundRect(chipX, chipY, chipL, chipA, chipL * 0.12);
  contexto.fillStyle = "rgba(190, 160, 96, 0.78)";
  contexto.fill();
  contexto.strokeStyle = "rgba(60, 48, 24, 0.5)";
  for (let i = 1; i < 3; i += 1) {
    contexto.beginPath();
    contexto.moveTo(chipX, chipY + (chipA / 3) * i);
    contexto.lineTo(chipX + chipL, chipY + (chipA / 3) * i);
    contexto.stroke();
  }
  contexto.beginPath();
  contexto.moveTo(chipX + chipL * 0.62, chipY);
  contexto.lineTo(chipX + chipL * 0.62, chipY + chipA);
  contexto.stroke();

  // numero, em relevo claro
  contexto.fillStyle = claro;
  contexto.font = `500 ${largura * 0.058}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.013}px`;
  contexto.fillText(dados.numeroParcial, largura * 0.075, altura * 0.665);

  contexto.font = `400 ${largura * 0.022}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.007}px`;
  contexto.fillStyle = fraco;
  contexto.fillText("PORTADOR", largura * 0.075, altura * 0.805);
  contexto.fillText("VALIDADE", largura * 0.63, altura * 0.805);

  contexto.fillStyle = meio;
  contexto.font = `500 ${largura * 0.029}px ${fonteMono}`;
  contexto.fillText(dados.nomePortador.toUpperCase().slice(0, 24), largura * 0.075, altura * 0.89);
  contexto.fillText(dados.validade, largura * 0.63, altura * 0.89);
}

function desenharVerso(canvas: HTMLCanvasElement, fonteMono: string, fonteDisplay: string) {
  const contexto = canvas.getContext("2d");
  if (!contexto) return;

  const largura = canvas.width;
  const altura = canvas.height;
  contexto.clearRect(0, 0, largura, altura);
  contexto.textBaseline = "alphabetic";

  // tarja magnetica
  const tarjaY = altura * 0.16;
  const tarjaA = altura * 0.20;
  contexto.fillStyle = "rgba(12, 14, 18, 0.96)";
  contexto.fillRect(0, tarjaY, largura, tarjaA);
  contexto.fillStyle = "rgba(255, 255, 255, 0.04)";
  contexto.fillRect(0, tarjaY, largura, altura * 0.012);

  // painel de assinatura, com hachura diagonal
  const painelX = largura * 0.075;
  const painelY = altura * 0.50;
  const painelL = largura * 0.60;
  const painelA = altura * 0.155;
  contexto.fillStyle = "rgba(238, 233, 222, 0.93)";
  contexto.fillRect(painelX, painelY, painelL, painelA);

  contexto.save();
  contexto.beginPath();
  contexto.rect(painelX, painelY, painelL, painelA);
  contexto.clip();
  contexto.strokeStyle = "rgba(150, 140, 120, 0.35)";
  contexto.lineWidth = Math.max(1, largura * 0.0014);
  for (let x = -painelA; x < painelL + painelA; x += largura * 0.018) {
    contexto.beginPath();
    contexto.moveTo(painelX + x, painelY + painelA);
    contexto.lineTo(painelX + x + painelA, painelY);
    contexto.stroke();
  }
  contexto.restore();

  contexto.fillStyle = "rgba(60, 54, 44, 0.55)";
  contexto.font = `400 ${largura * 0.019}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.006}px`;
  contexto.fillText("ASSINATURA DO PORTADOR", painelX + largura * 0.014, painelY + painelA - altura * 0.028);

  // caixa do codigo de seguranca, ao lado do painel
  const caixaX = painelX + painelL + largura * 0.022;
  contexto.strokeStyle = "rgba(200, 164, 92, 0.55)";
  contexto.lineWidth = Math.max(1, largura * 0.0016);
  contexto.strokeRect(caixaX, painelY, largura * 0.12, painelA);
  contexto.fillStyle = "rgba(226, 214, 188, 0.72)";
  contexto.font = `500 ${largura * 0.020}px ${fonteMono}`;
  contexto.fillText("CVV", caixaX + largura * 0.012, painelY + altura * 0.05);
  contexto.font = `500 ${largura * 0.034}px ${fonteMono}`;
  contexto.fillText("•••", caixaX + largura * 0.012, painelY + altura * 0.115);

  // letra miuda
  contexto.fillStyle = "rgba(214, 202, 178, 0.42)";
  contexto.font = `400 ${largura * 0.0175}px ${fonteMono}`;
  contexto.letterSpacing = `${largura * 0.004}px`;
  contexto.fillText(
    "PECA DE DEMONSTRACAO. AUTORIZACAO SIMULADA, NENHUM VALOR REAL E MOVIMENTADO.",
    painelX,
    altura * 0.775
  );
  contexto.fillText("EMITIDO PARA A DISCIPLINA DE APLICACOES NATIVAS", painelX, altura * 0.825);

  contexto.strokeStyle = "rgba(200, 164, 92, 0.35)";
  contexto.lineWidth = Math.max(1, largura * 0.0012);
  contexto.beginPath();
  contexto.moveTo(painelX, altura * 0.865);
  contexto.lineTo(largura - painelX, altura * 0.865);
  contexto.stroke();

  contexto.fillStyle = "rgba(226, 214, 188, 0.6)";
  contexto.font = `600 ${largura * 0.034}px ${fonteDisplay}`;
  contexto.letterSpacing = `${largura * 0.012}px`;
  contexto.fillText("AVAL", painelX, altura * 0.945);
}

export default function PlacaGravada({
  bandeira = "DESCONHECIDA",
  numeroParcial,
  ultimosQuatro,
  nomePortador = "SEU NOME AQUI",
  validade = "12/30",
  modo = "palco",
  altura = "100%"
}: Props) {
  const container = useRef<HTMLDivElement>(null);
  const pintar = useRef<(() => void) | null>(null);

  const numeroNaFace =
    numeroParcial && numeroParcial.trim().length > 0
      ? numeroParcial
      : `oooo oooo oooo ${ultimosQuatro ?? "oooo"}`.replace(/o/g, "•");

  useEffect(() => {
    const alvo = container.current;
    if (!alvo) return;

    const paradoPorPreferencia = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

    let renderizador: THREE.WebGLRenderer;
    try {
      renderizador = new THREE.WebGLRenderer({ antialias: true, alpha: true, powerPreference: "low-power" });
    } catch {
      return; // sem WebGL o palco fica com o proprio fundo, sem quebrar a pagina
    }

    renderizador.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    renderizador.setSize(alvo.clientWidth, alvo.clientHeight, false);
    alvo.appendChild(renderizador.domElement);
    renderizador.domElement.style.width = "100%";
    renderizador.domElement.style.height = "100%";

    const cena = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(32, alvo.clientWidth / alvo.clientHeight, 0.1, 40);
    camera.position.set(0, 0, modo === "palco" ? 4.0 : 2.9);

    const face = document.createElement("canvas");
    face.width = 1024;
    face.height = Math.round(1024 / PROPORCAO);
    const textura = new THREE.CanvasTexture(face);
    textura.colorSpace = THREE.SRGBColorSpace;
    textura.anisotropy = renderizador.capabilities.getMaxAnisotropy();

    const verso = document.createElement("canvas");
    verso.width = face.width;
    verso.height = face.height;
    const texturaVerso = new THREE.CanvasTexture(verso);
    texturaVerso.colorSpace = THREE.SRGBColorSpace;
    texturaVerso.anisotropy = textura.anisotropy;

    const material = new THREE.ShaderMaterial({
      vertexShader: VERTICE,
      fragmentShader: FRAGMENTO,
      transparent: true,
      side: THREE.DoubleSide,
      uniforms: {
        uFace: { value: textura },
        uVerso: { value: texturaVerso },
        uTempo: { value: 0 },
        uAcoBaixo: { value: new THREE.Color("#0a1614") },
        uAcoAlto: { value: new THREE.Color("#1d3b35") },
        uLaton: { value: new THREE.Color("#c69c52") },
        uBrilho: { value: modo === "palco" ? 1.0 : 0.95 }
      }
    });

    const peca = new THREE.Mesh(new THREE.PlaneGeometry(PROPORCAO * 1.28, 1.28, 1, 1), material);
    cena.add(peca);

    // A peca nao gira sozinha: quem gira e quem arrasta. Soltar o ponteiro
    // mantem o giro por inercia ate o atrito zerar.
    const rotacao = { x: -0.08, y: modo === "palco" ? -0.55 : -0.18 };
    const velocidade = { x: 0, y: 0 };
    const ultimo = { x: 0, y: 0 };
    let arrastando = false;

    const tela = renderizador.domElement;
    tela.style.cursor = "grab";
    tela.style.touchAction = "pan-y";

    function aoPressionar(evento: PointerEvent) {
      arrastando = true;
      ultimo.x = evento.clientX;
      ultimo.y = evento.clientY;
      velocidade.x = 0;
      velocidade.y = 0;
      tela.style.cursor = "grabbing";
      tela.setPointerCapture(evento.pointerId);
    }

    function aoArrastar(evento: PointerEvent) {
      if (!arrastando) return;
      velocidade.y = (evento.clientX - ultimo.x) * 0.007;
      velocidade.x = (evento.clientY - ultimo.y) * 0.005;
      ultimo.x = evento.clientX;
      ultimo.y = evento.clientY;
      rotacao.y += velocidade.y;
      rotacao.x = Math.min(0.7, Math.max(-0.7, rotacao.x + velocidade.x));
    }

    function aoSoltar(evento: PointerEvent) {
      if (!arrastando) return;
      arrastando = false;
      tela.style.cursor = "grab";
      if (tela.hasPointerCapture(evento.pointerId)) tela.releasePointerCapture(evento.pointerId);
    }

    tela.addEventListener("pointerdown", aoPressionar);
    tela.addEventListener("pointermove", aoArrastar);
    tela.addEventListener("pointerup", aoSoltar);
    tela.addEventListener("pointercancel", aoSoltar);

    const relogio = new THREE.Clock();
    let quadro = 0;

    function desenhar() {
      quadro = requestAnimationFrame(desenhar);

      if (!arrastando) {
        rotacao.y += velocidade.y;
        rotacao.x = Math.min(0.7, Math.max(-0.7, rotacao.x + velocidade.x));
        velocidade.x *= 0.93;
        velocidade.y *= 0.93;
        if (Math.abs(velocidade.x) < 0.00002) velocidade.x = 0;
        if (Math.abs(velocidade.y) < 0.00002) velocidade.y = 0;
      }

      const parado = velocidade.x === 0 && velocidade.y === 0 && !arrastando;
      // sob preferencia por menos movimento, so redesenha quando o usuario mexe
      if (paradoPorPreferencia && parado) return;

      if (!paradoPorPreferencia) {
        material.uniforms.uTempo.value = relogio.getElapsedTime();
      }

      peca.rotation.set(rotacao.x, rotacao.y, 0);
      renderizador.render(cena, camera);
    }

    const observador = new ResizeObserver(() => {
      if (!alvo.clientWidth || !alvo.clientHeight) return;
      renderizador.setSize(alvo.clientWidth, alvo.clientHeight, false);
      camera.aspect = alvo.clientWidth / alvo.clientHeight;
      camera.updateProjectionMatrix();
      peca.rotation.set(rotacao.x, rotacao.y, 0);
      renderizador.render(cena, camera);
    });
    observador.observe(alvo);

    pintar.current = () => {
      const raiz = getComputedStyle(document.documentElement);
      const fonteMono = raiz.getPropertyValue("--fonte-mono").trim() || "ui-monospace, monospace";
      const fonteDisplay = raiz.getPropertyValue("--fonte-display").trim() || "Georgia, serif";

      desenharFace(
        face,
        {
          bandeira: (alvo.dataset.bandeira as Bandeira) ?? "DESCONHECIDA",
          numeroParcial: alvo.dataset.numero ?? "",
          nomePortador: alvo.dataset.portador ?? "",
          validade: alvo.dataset.validade ?? ""
        },
        fonteMono,
        fonteDisplay
      );
      desenharVerso(verso, fonteMono, fonteDisplay);
      textura.needsUpdate = true;
      texturaVerso.needsUpdate = true;
    };
    pintar.current();

    if (document.fonts?.ready) {
      document.fonts.ready.then(() => pintar.current?.());
    }

    material.uniforms.uTempo.value = paradoPorPreferencia ? 2.4 : 0;
    quadro = requestAnimationFrame(desenhar);

    return () => {
      cancelAnimationFrame(quadro);
      observador.disconnect();
      tela.removeEventListener("pointerdown", aoPressionar);
      tela.removeEventListener("pointermove", aoArrastar);
      tela.removeEventListener("pointerup", aoSoltar);
      tela.removeEventListener("pointercancel", aoSoltar);
      pintar.current = null;
      peca.geometry.dispose();
      material.dispose();
      textura.dispose();
      texturaVerso.dispose();
      renderizador.dispose();
      renderizador.domElement.remove();
    };
  }, [modo]);

  useEffect(() => {
    const alvo = container.current;
    if (!alvo) return;
    alvo.dataset.bandeira = bandeira;
    alvo.dataset.numero = numeroNaFace;
    alvo.dataset.portador = nomePortador;
    alvo.dataset.validade = validade;
    pintar.current?.();
  }, [bandeira, numeroNaFace, nomePortador, validade]);

  return (
    <div
      ref={container}
      style={{ width: "100%", height: typeof altura === "number" ? `${altura}px` : altura }}
      aria-hidden="true"
    />
  );
}
