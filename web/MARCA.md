# Aval

Identidade da interface do gateway. Este documento existe para que as decisoes
visuais tenham motivo escrito, e nao virem gosto pessoal na proxima alteracao.

## Nome

**Aval.** Em portugues bancario, dar o aval e garantir uma operacao com a
propria palavra. E exatamente o que um autorizador de pagamentos faz: recebe a
tentativa, decide, e responde sim ou nao. O nome descreve a funcao do produto em
uma palavra que ja existe no vocabulario de quem trabalha com dinheiro, sem
precisar de sufixo em ingles nem de neologismo.

Assinatura completa: **Aval · Instituicao de Pagamento**.
Frase de apoio: *A palavra final de cada transacao.*

## Simbolo

Um carimbo: dois aneis concentricos e, no centro, um "A" reduzido ao vertice e a
travessa, sem bojo. O carimbo e o gesto fisico do aval, o que se bate embaixo de
um documento para dize-lo valido. O anel externo e grosso, o interno e fino, e a
diferenca de peso entre os dois e o que faz a marca continuar legivel a 16
pixels.

O simbolo nunca aparece preenchido de cor chapada, nunca ganha degrade e nunca e
usado sem respiro equivalente ao raio do anel interno.

## Cor

| Papel | Nome | Valor |
|---|---|---|
| Primaria | Verde-cofre | `#0e4a43` |
| Cerimonial | Latao | `#a9803a` |
| Documento | Papel | `#fbf7ef` |
| Tinta | Grafite | `#191c22` |
| Fundo | Branco frio | `#f6f6f4` |

O verde-cofre foi escolhido por exclusao deliberada: laranja, vermelho, roxo e
azul-royal ja pertencem aos grandes bancos brasileiros na cabeca de qualquer
pessoa daqui. O verde profundo com latao remete a cofre, a couro de livro razao
e a papel de apolice, sem colar em ninguem.

O latao nunca preenche area grande. Ele e fio, borda, gravacao e carimbo.

## Tipografia

- **Bodoni Moda** no logotipo e nos titulos. Didone e a letra gravada de cedula
  e de titulo ao portador. Casa com o guilhoche da peca tridimensional porque
  vem da mesma tradicao de impressao de seguranca.
- **Instrument Sans** no texto de interface. Neutra, sem opiniao, para nao
  competir com o titulo.
- **IBM Plex Mono** em todo numero, codigo e valor. Foi desenhada para uma
  instituicao e tem cara de extrato, que e o que a maioria dos numeros aqui e.

Valor monetario e sempre monoespacado, alinhado a direita e com numeral tabular.

## Movimento

Tres regras.

1. **Uma vez, com motivo.** Animacao marca acontecimento, nao decora espera. O
   carimbo bate quando a transacao e aprovada, e so.
2. **Curta.** Nada acima de 420 ms. Transicao de estado fica em 140 ms.
3. **Silenciavel.** Tudo respeita `prefers-reduced-motion`, inclusive a peca
   tridimensional, que sob essa preferencia so se move quando arrastada.

## Voz

Frase curta, verbo ativo, sem venda. O botao diz o que acontece ao ser apertado,
e o aviso seguinte usa a mesma palavra: quem aperta "Capturar" le "Cobranca
capturada". Erro explica o que houve e o que fazer, sem pedir desculpa e sem
culpar quem esta lendo.
