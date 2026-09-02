/**
 * O carimbo: anel grosso por fora, fio fino por dentro e um "A" reduzido ao
 * vertice e a travessa. A diferenca de peso entre os aneis e o que mantem a
 * marca legivel quando ela vira favicon.
 */
export function Simbolo({ tamanho = 26 }: { tamanho?: number }) {
  return (
    <svg
      width={tamanho}
      height={tamanho}
      viewBox="0 0 40 40"
      fill="none"
      aria-hidden="true"
      className="simbolo"
    >
      <circle cx="20" cy="20" r="18.2" stroke="currentColor" strokeWidth="1.9" />
      <circle cx="20" cy="20" r="14.4" stroke="currentColor" strokeWidth="0.75" opacity="0.65" />
      <path d="M13 27.4 L20 12.2 L27 27.4" stroke="currentColor" strokeWidth="2.2" strokeLinecap="square" />
      <path d="M15.1 22.6 H24.9" stroke="currentColor" strokeWidth="1.7" strokeLinecap="square" />
    </svg>
  );
}

export default function Marca({ complemento }: { complemento?: string }) {
  return (
    <span className="marca">
      <Simbolo />
      <span className="marca-nome">Aval</span>
      {complemento && (
        <>
          <span className="marca-fio" aria-hidden="true" />
          <span className="marca-complemento">{complemento}</span>
        </>
      )}
    </span>
  );
}
