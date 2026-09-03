"use client";

import { useEffect, useState } from "react";
import { useRouter } from "next/navigation";
import PlacaGravada from "@/components/PlacaGravada";
import FaixaGuilloche from "@/components/FaixaGuilloche";
import Marca from "@/components/Marca";
import { api, ErroDaApi } from "@/lib/api";
import { gravarSessao, lerSessao } from "@/lib/sessao";

type Aba = "entrar" | "criar";

export default function Entrada() {
  const router = useRouter();
  const [aba, setAba] = useState<Aba>("entrar");
  const [email, setEmail] = useState("");
  const [senha, setSenha] = useState("");
  const [nomeEstabelecimento, setNomeEstabelecimento] = useState("");
  const [erro, setErro] = useState<string | null>(null);
  const [campos, setCampos] = useState<Record<string, string>>({});
  const [enviando, setEnviando] = useState(false);

  useEffect(() => {
    if (lerSessao()) router.replace("/painel");
  }, [router]);

  async function enviar(evento: React.FormEvent) {
    evento.preventDefault();
    setErro(null);
    setCampos({});
    setEnviando(true);

    try {
      if (aba === "criar") {
        await api.registrar({ email, senha, nomeEstabelecimento });
      }

      const token = await api.entrar({ email, senha });
      const eu = await api.eu(token.token);

      gravarSessao({
        token: token.token,
        email: eu.email,
        nomeEstabelecimento: eu.nomeEstabelecimento,
        expiraEm: token.expiraEm
      });

      router.push("/painel");
    } catch (falha) {
      if (falha instanceof ErroDaApi) {
        setErro(falha.message);
        setCampos(falha.campos);
      } else {
        setErro("Algo saiu do previsto. Tente de novo.");
      }
      setEnviando(false);
    }
  }

  function trocarAba(proxima: Aba) {
    setAba(proxima);
    setErro(null);
    setCampos({});
  }

  function preencherDemonstracao() {
    setAba("entrar");
    setEmail("demo@aval.app");
    setSenha("demonstracao2026");
    setErro(null);
    setCampos({});
  }

  return (
    <main className="entrada">
      <section className="entrada-palco">
        <div className="entrada-marca">
          <Marca complemento="Instituicao de Pagamento" />
        </div>

        <PlacaGravada modo="palco" nomePortador="Ricardo Figueiredo" validade="12/30" />

        <div className="entrada-assinatura">
          <span className="etiqueta">Autorizacao simulada</span>
          <p>
            Nenhum dinheiro real circula por aqui. A decisao de aprovar ou recusar sai de regras fixas,
            para que cada tipo de recusa possa ser reproduzido na hora da demonstracao.
          </p>
          <p className="dica-arraste">Arraste a peca para girar e ver o verso</p>
        </div>
      </section>

      <section className="entrada-lado">
        <span className="etiqueta entra">A palavra final de cada transacao</span>
        <h1 className="entra" style={{ animationDelay: "70ms" }}>
          Autorizar e so o comeco da conversa.
        </h1>
        <p className="entrada-intro entra" style={{ animationDelay: "140ms" }}>
          Crie a cobranca em cartao ou Pix, veja o autorizador decidir na hora e acompanhe captura,
          cancelamento e estorno na linha do tempo de cada transacao.
        </p>

        <div className="abas entra" style={{ animationDelay: "210ms" }} role="tablist">
          <button
            className="aba"
            role="tab"
            type="button"
            aria-selected={aba === "entrar"}
            data-ativa={aba === "entrar" ? "sim" : "nao"}
            onClick={() => trocarAba("entrar")}
          >
            Entrar
          </button>
          <button
            className="aba"
            role="tab"
            type="button"
            aria-selected={aba === "criar"}
            data-ativa={aba === "criar" ? "sim" : "nao"}
            onClick={() => trocarAba("criar")}
          >
            Criar conta
          </button>
        </div>

        <form className="formulario entra" style={{ animationDelay: "270ms" }} onSubmit={enviar}>
          {aba === "criar" && (
            <label className="campo">
              <span>Estabelecimento</span>
              <input
                value={nomeEstabelecimento}
                onChange={(evento) => setNomeEstabelecimento(evento.target.value)}
                placeholder="Padaria do Ricardo"
                autoComplete="organization"
                required
              />
              {campos.nomeEstabelecimento && <em className="campo-erro">{campos.nomeEstabelecimento}</em>}
            </label>
          )}

          <label className="campo">
            <span>E-mail</span>
            <input
              type="email"
              value={email}
              onChange={(evento) => setEmail(evento.target.value)}
              placeholder="loja@exemplo.com"
              autoComplete="email"
              required
            />
            {campos.email && <em className="campo-erro">{campos.email}</em>}
          </label>

          <label className="campo">
            <span>Senha</span>
            <input
              type="password"
              value={senha}
              onChange={(evento) => setSenha(evento.target.value)}
              placeholder={aba === "criar" ? "no minimo 8 caracteres" : ""}
              autoComplete={aba === "criar" ? "new-password" : "current-password"}
              required
            />
            {campos.senha && <em className="campo-erro">{campos.senha}</em>}
          </label>

          {erro && <p className="aviso">{erro}</p>}

          <button className="botao" data-largura="cheia" type="submit" disabled={enviando}>
            {enviando ? "Um instante" : aba === "criar" ? "Criar conta e entrar" : "Entrar"}
          </button>
        </form>

        {aba === "entrar" && (
          <aside className="acesso-demo entra" style={{ animationDelay: "310ms" }}>
            <div>
              <span className="etiqueta">Visita guiada</span>
              <p>Veja cobrancas, Pix, parcelamentos, webhooks e links ja em movimento.</p>
              <code>demo@aval.app · demonstracao2026</code>
            </div>
            <button className="botao" data-tom="vazado" type="button" onClick={preencherDemonstracao}>
              Preencher acesso
            </button>
          </aside>
        )}

        <p className="etiqueta link-documentacao entra" style={{ animationDelay: "330ms" }}>
          <a href="/swagger-ui.html">Documentacao da API</a>
        </p>

        <div className="entrada-fio">
          <FaixaGuilloche />
        </div>
      </section>
    </main>
  );
}
