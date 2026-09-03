package br.com.ricardofigueiredo.gateway.webhook.dto;

import br.com.ricardofigueiredo.gateway.webhook.EndpointWebhook;

import java.time.Instant;

/**
 * O segredo aparece por inteiro apenas na resposta do cadastro. Nas leituras
 * seguintes sai mascarado, porque nao ha motivo para uma tela de listagem
 * carregar credencial em texto claro.
 */
public record EndpointResponse(String codigo, String url, String descricao, boolean ativo,
                               String segredo, Instant criadoEm) {

    public static EndpointResponse comSegredo(EndpointWebhook endpoint) {
        return new EndpointResponse(endpoint.getCodigo(), endpoint.getUrl(), endpoint.getDescricao(),
                endpoint.isAtivo(), endpoint.getSegredo(), endpoint.getCriadoEm());
    }

    public static EndpointResponse mascarado(EndpointWebhook endpoint) {
        String segredo = endpoint.getSegredo();
        String visivel = segredo.length() <= 10 ? segredo : segredo.substring(0, 10) + "...";

        return new EndpointResponse(endpoint.getCodigo(), endpoint.getUrl(), endpoint.getDescricao(),
                endpoint.isAtivo(), visivel, endpoint.getCriadoEm());
    }
}
