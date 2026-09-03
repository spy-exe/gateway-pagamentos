"use client";

import { useEffect, useState } from "react";
import QRCode from "qrcode";

/*
  O QR sai do proprio copia e cola, sem servico externo: o payload EMV ja e a
  string que o aplicativo do banco espera ler. Correcao de erro em nivel medio
  e o que o manual do Pix recomenda, por ser o equilibrio entre tolerar
  sujeira na tela e nao inchar a matriz.
*/
export default function QrPix({ copiaECola }: { copiaECola: string }) {
  const [imagem, setImagem] = useState<string | null>(null);
  const [copiado, setCopiado] = useState(false);

  useEffect(() => {
    let ativo = true;

    QRCode.toString(copiaECola, {
      type: "svg",
      errorCorrectionLevel: "M",
      margin: 0,
      color: { dark: "#241f18", light: "#00000000" }
    })
      .then((svg) => {
        if (ativo) setImagem(svg);
      })
      .catch(() => {
        if (ativo) setImagem(null);
      });

    return () => {
      ativo = false;
    };
  }, [copiaECola]);

  async function copiar() {
    try {
      await navigator.clipboard.writeText(copiaECola);
      setCopiado(true);
      window.setTimeout(() => setCopiado(false), 2200);
    } catch {
      setCopiado(false);
    }
  }

  return (
    <div className="qr-pix">
      {imagem ? (
        <div className="qr-pix-matriz" dangerouslySetInnerHTML={{ __html: imagem }} />
      ) : (
        <p className="qr-pix-falha">Nao foi possivel desenhar o QR.</p>
      )}

      <button className="qr-pix-copiar" type="button" onClick={copiar}>
        {copiado ? "Codigo copiado" : "Copiar codigo Pix"}
      </button>
    </div>
  );
}
