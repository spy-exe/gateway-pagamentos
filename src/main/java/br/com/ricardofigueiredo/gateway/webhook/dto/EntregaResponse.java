package br.com.ricardofigueiredo.gateway.webhook.dto;

import br.com.ricardofigueiredo.gateway.webhook.EntregaWebhook;
import br.com.ricardofigueiredo.gateway.webhook.SituacaoDaEntrega;

import java.time.Instant;

public record EntregaResponse(String codigo, String evento, SituacaoDaEntrega situacao, int tentativas,
                              Integer ultimoCodigoHttp, String ultimaFalha, Instant proximaTentativaEm,
                              Instant criadoEm, Instant concluidoEm, String corpo) {

    public static EntregaResponse de(EntregaWebhook entrega) {
        return new EntregaResponse(entrega.getCodigo(), entrega.getEvento(), entrega.getSituacao(),
                entrega.getTentativas(), entrega.getUltimoCodigoHttp(), entrega.getUltimaFalha(),
                entrega.getProximaTentativaEm(), entrega.getCriadoEm(), entrega.getConcluidoEm(),
                entrega.getCorpo());
    }
}
