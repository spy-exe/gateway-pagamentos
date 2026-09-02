import type { Metadata } from "next";
import { Bodoni_Moda, IBM_Plex_Mono, Instrument_Sans } from "next/font/google";
import "./globals.css";

const display = Bodoni_Moda({
  subsets: ["latin"],
  weight: ["400", "500", "600"],
  variable: "--fonte-bodoni",
  display: "swap"
});

const texto = Instrument_Sans({
  subsets: ["latin"],
  variable: "--fonte-instrument",
  display: "swap"
});

const mono = IBM_Plex_Mono({
  subsets: ["latin"],
  weight: ["400", "500"],
  variable: "--fonte-plex",
  display: "swap"
});

export const metadata: Metadata = {
  title: "Aval · Instituicao de Pagamento",
  description:
    "Painel do gateway Aval: cria cobrancas em cartao e Pix, autoriza, captura, cancela e estorna."
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="pt-BR" className={`${display.variable} ${texto.variable} ${mono.variable}`}>
      <body>{children}</body>
    </html>
  );
}
