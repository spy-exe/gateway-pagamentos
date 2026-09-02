/**
 * Fio de seguranca: a mesma familia de curvas da gravacao da peca, achatada em
 * uma faixa de um pixel e meio. Serve de rodape de secao, como a orla de uma
 * cedula, e nao carrega informacao nenhuma.
 */
export default function FaixaGuilloche({ altura = 14 }: { altura?: number }) {
  const largura = 1200;
  const meio = altura / 2;
  const curvas = [0, 1, 2].map((indice) => {
    const amplitude = meio * (0.82 - indice * 0.22);
    const passo = 26 + indice * 9;
    const deslocamento = indice * 7;
    let caminho = `M0 ${meio}`;
    for (let x = 0; x <= largura; x += passo) {
      const alto = meio - amplitude;
      const baixo = meio + amplitude;
      caminho += ` Q ${x + passo / 4 + deslocamento} ${indice % 2 === 0 ? alto : baixo}, ${x + passo / 2} ${meio}`;
      caminho += ` Q ${x + (passo * 3) / 4 + deslocamento} ${indice % 2 === 0 ? baixo : alto}, ${x + passo} ${meio}`;
    }
    return caminho;
  });

  return (
    <svg
      className="faixa-guilloche"
      viewBox={`0 0 ${largura} ${altura}`}
      preserveAspectRatio="none"
      aria-hidden="true"
    >
      {curvas.map((caminho, indice) => (
        <path key={indice} d={caminho} fill="none" stroke="currentColor" strokeWidth="0.7" opacity={0.8 - indice * 0.22} />
      ))}
    </svg>
  );
}
