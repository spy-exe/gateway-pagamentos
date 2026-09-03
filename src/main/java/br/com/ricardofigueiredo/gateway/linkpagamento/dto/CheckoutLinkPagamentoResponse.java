package br.com.ricardofigueiredo.gateway.linkpagamento.dto;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.linkpagamento.LinkPagamento;
import br.com.ricardofigueiredo.gateway.linkpagamento.SituacaoLinkPagamento;

import java.time.Instant;

public record CheckoutLinkPagamentoResponse(
        String codigo,
        String estabelecimento,
        String descricao,
        long valorEmCentavos,
        MetodoPagamento metodo,
        int parcelasMaximas,
        SituacaoLinkPagamento situacao,
        Instant expiraEm) {

    public static CheckoutLinkPagamentoResponse de(LinkPagamento link, Instant agora) {
        return new CheckoutLinkPagamentoResponse(
                link.getCodigo(), link.getUsuario().getNomeEstabelecimento(), link.getDescricao(),
                link.getValorEmCentavos(), link.getMetodo(), link.getParcelasMaximas(),
                link.situacao(agora), link.getExpiraEm());
    }
}
