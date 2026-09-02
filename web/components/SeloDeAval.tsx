"use client";

import { dataCurta } from "@/lib/formato";

/**
 * O momento da marca: quando a transacao passa, o carimbo bate no comprovante.
 * Acontece uma vez, na entrada do elemento, e some sob prefers-reduced-motion.
 */
export default function SeloDeAval({
  codigoAutorizacao,
  quando,
  recusado = false
}: {
  codigoAutorizacao?: string;
  quando: string;
  recusado?: boolean;
}) {
  const legenda = recusado ? "SEM AVAL" : "AVAL CONCEDIDO";

  return (
    <div className="selo-aval" data-recusado={recusado ? "sim" : "nao"}>
      <svg viewBox="0 0 160 160" role="img" aria-label={legenda}>
        <defs>
          <path id="trilhaSelo" d="M80,80 m-58,0 a58,58 0 1,1 116,0 a58,58 0 1,1 -116,0" />
        </defs>

        <circle cx="80" cy="80" r="72" fill="none" stroke="currentColor" strokeWidth="3.4" />
        <circle cx="80" cy="80" r="64" fill="none" stroke="currentColor" strokeWidth="1" opacity="0.7" />

        <text className="selo-aval-curva">
          <textPath href="#trilhaSelo" startOffset="25%" textAnchor="middle">
            {legenda}
          </textPath>
        </text>

        <path d="M62 96 L80 56 L98 96" fill="none" stroke="currentColor" strokeWidth="4.6" strokeLinecap="square" />
        <path d="M67.6 83.5 H92.4" fill="none" stroke="currentColor" strokeWidth="3.6" strokeLinecap="square" />

        <text className="selo-aval-rodape" x="80" y="114" textAnchor="middle">
          {codigoAutorizacao ?? "000000"}
        </text>
        <text className="selo-aval-rodape" x="80" y="128" textAnchor="middle">
          {dataCurta(quando)}
        </text>
      </svg>
    </div>
  );
}
