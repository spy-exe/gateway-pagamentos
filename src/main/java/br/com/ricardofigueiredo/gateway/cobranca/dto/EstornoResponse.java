package br.com.ricardofigueiredo.gateway.cobranca.dto;

import br.com.ricardofigueiredo.gateway.cobranca.Estorno;

import java.time.Instant;

public record EstornoResponse(String codigo, long valorEmCentavos, String motivo, Instant criadoEm) {

    public static EstornoResponse de(Estorno estorno) {
        return new EstornoResponse(estorno.getCodigo(), estorno.getValorEmCentavos(),
                estorno.getMotivo(), estorno.getCriadoEm());
    }
}
