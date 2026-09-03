"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import FaixaGuilloche from "@/components/FaixaGuilloche";
import Marca from "@/components/Marca";
import { useSessao } from "@/lib/sessao";

export default function PainelLayout({ children }: { children: React.ReactNode }) {
  const { sessao, verificando, sair } = useSessao();
  const caminho = usePathname();

  if (verificando || !sessao) {
    return <p className="carregando">Conferindo a sessao</p>;
  }

  return (
    <div className="painel">
      <header className="painel-topo">
        <Link href="/painel" className="painel-marca">
          <Marca complemento={sessao.nomeEstabelecimento} />
        </Link>

        <nav className="painel-nav">
          <Link href="/painel" aria-current={caminho === "/painel" ? "page" : undefined}>
            Cobrancas
          </Link>
          <Link href="/painel/nova" aria-current={caminho === "/painel/nova" ? "page" : undefined}>
            Nova cobranca
          </Link>
          <Link href="/painel/links" aria-current={caminho === "/painel/links" ? "page" : undefined}>
            Links
          </Link>
          <Link href="/painel/webhooks" aria-current={caminho === "/painel/webhooks" ? "page" : undefined}>
            Webhooks
          </Link>
          <a href="/swagger-ui.html">API</a>
          <button className="botao" data-tom="vazado" type="button" onClick={sair}>
            Sair
          </button>
        </nav>
      </header>

      <div className="painel-fio">
        <FaixaGuilloche altura={10} />
      </div>

      <main className="painel-corpo">{children}</main>
    </div>
  );
}
