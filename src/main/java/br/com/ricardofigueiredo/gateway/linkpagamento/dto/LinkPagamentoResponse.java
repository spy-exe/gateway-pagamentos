package br.com.ricardofigueiredo.gateway.linkpagamento.dto;

import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.linkpagamento.LinkPagamento;
import br.com.ricardofigueiredo.gateway.linkpagamento.SituacaoLinkPagamento;

import java.time.Instant;

public record LinkPagamentoResponse(
        String codigo,
        String descricao,
        long valorEmCentavos,
        MetodoPagamento metodo,
        int parcelasMaximas,
        Integer limiteDeUsos,
        int usos,
        boolean ativo,
        SituacaoLinkPagamento situacao,
        Instant expiraEm,
        Instant criadoEm,
        Instant atualizadoEm) {

    public static LinkPagamentoResponse de(LinkPagamento link, Instant agora) {
        return new LinkPagamentoResponse(
                link.getCodigo(), link.getDescricao(), link.getValorEmCentavos(), link.getMetodo(),
                link.getParcelasMaximas(), link.getLimiteDeUsos(), link.getUsos(), link.isAtivo(),
                link.situacao(agora), link.getExpiraEm(), link.getCriadoEm(), link.getAtualizadoEm());
    }
}
