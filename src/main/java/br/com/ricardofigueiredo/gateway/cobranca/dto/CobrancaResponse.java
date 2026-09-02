package br.com.ricardofigueiredo.gateway.cobranca.dto;

import br.com.ricardofigueiredo.gateway.autorizacao.Bandeira;
import br.com.ricardofigueiredo.gateway.cobranca.Cobranca;
import br.com.ricardofigueiredo.gateway.cobranca.MetodoPagamento;
import br.com.ricardofigueiredo.gateway.cobranca.StatusCobranca;

import java.time.Instant;

public record CobrancaResponse(
        String codigo,
        long valorEmCentavos,
        long valorEstornadoEmCentavos,
        long saldoEstornavelEmCentavos,
        String moeda,
        String descricao,
        MetodoPagamento metodo,
        StatusCobranca status,
        String motivoRecusa,
        String descricaoDaRecusa,
        String codigoAutorizacao,
        boolean capturaAutomatica,
        CartaoResponse cartao,
        Instant criadoEm,
        Instant atualizadoEm) {

    public record CartaoResponse(Bandeira bandeira, String bin, String ultimosQuatro, String nomePortador) {
    }

    public static CobrancaResponse de(Cobranca cobranca) {
        CartaoResponse cartao = cobranca.getUltimosQuatro() == null ? null : new CartaoResponse(
                cobranca.getBandeira(), cobranca.getBin(), cobranca.getUltimosQuatro(), cobranca.getNomePortador());

        return new CobrancaResponse(
                cobranca.getCodigo(),
                cobranca.getValorEmCentavos(),
                cobranca.getValorEstornadoEmCentavos(),
                cobranca.saldoEstornavelEmCentavos(),
                cobranca.getMoeda(),
                cobranca.getDescricao(),
                cobranca.getMetodo(),
                cobranca.getStatus(),
                cobranca.getMotivoRecusa() == null ? null : cobranca.getMotivoRecusa().name(),
                cobranca.getMotivoRecusa() == null ? null : cobranca.getMotivoRecusa().getDescricao(),
                cobranca.getCodigoAutorizacao(),
                cobranca.isCapturaAutomatica(),
                cartao,
                cobranca.getCriadoEm(),
                cobranca.getAtualizadoEm());
    }
}
