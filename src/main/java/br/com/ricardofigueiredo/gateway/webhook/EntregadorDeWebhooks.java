package br.com.ricardofigueiredo.gateway.webhook;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * Tira as entregas da caixa de saida e bate na porta do estabelecimento.
 *
 * Roda em intervalo curto, pega um lote pequeno e nunca segura a resposta de
 * quem criou a cobranca. Quem responde 2xx sai da fila; quem nao responde
 * volta para ela com a espera dobrada, ate acabarem as tentativas.
 */
@Component
public class EntregadorDeWebhooks {

    private static final Logger log = LoggerFactory.getLogger(EntregadorDeWebhooks.class);
    private static final int TAMANHO_DO_LOTE = 20;

    private final EntregaWebhookRepository entregaRepository;
    private final HttpClient cliente;
    private final Duration tempoLimite;

    public EntregadorDeWebhooks(EntregaWebhookRepository entregaRepository,
                                @Value("${gateway.webhook.tempo-limite-segundos:10}") long tempoLimiteSegundos) {
        this.entregaRepository = entregaRepository;
        this.tempoLimite = Duration.ofSeconds(tempoLimiteSegundos);
        this.cliente = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    @Scheduled(fixedDelayString = "${gateway.webhook.intervalo-ms:15000}")
    @Transactional
    public void rodada() {
        List<EntregaWebhook> lote =
                entregaRepository.pendentes(Instant.now(), PageRequest.of(0, TAMANHO_DO_LOTE));

        lote.forEach(this::entregar);
    }

    void entregar(EntregaWebhook entrega) {
        EndpointWebhook endpoint = entrega.getEndpoint();
        String assinatura = AssinaturaDeWebhook.gerar(endpoint.getSegredo(), entrega.getCorpo(), Instant.now());

        HttpRequest requisicao = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(tempoLimite)
                .header("Content-Type", "application/json")
                .header("User-Agent", "Aval-Webhooks/1.0")
                .header("Aval-Evento", entrega.getEvento())
                .header("Aval-Entrega", entrega.getCodigo())
                .header(AssinaturaDeWebhook.CABECALHO, assinatura)
                .POST(HttpRequest.BodyPublishers.ofString(entrega.getCorpo()))
                .build();

        try {
            HttpResponse<String> resposta = cliente.send(requisicao, HttpResponse.BodyHandlers.ofString());

            if (resposta.statusCode() >= 200 && resposta.statusCode() < 300) {
                entrega.registrarSucesso(resposta.statusCode());
                log.info("entrega {} aceita com {}", entrega.getCodigo(), resposta.statusCode());
            } else {
                entrega.registrarFalha(resposta.statusCode(), "o endpoint respondeu " + resposta.statusCode());
                log.warn("entrega {} recusada com {}", entrega.getCodigo(), resposta.statusCode());
            }
        } catch (InterruptedException excecao) {
            Thread.currentThread().interrupt();
            entrega.registrarFalha(null, "entrega interrompida");
        } catch (Exception excecao) {
            entrega.registrarFalha(null, excecao.getClass().getSimpleName() + ": " + excecao.getMessage());
            log.warn("entrega {} falhou: {}", entrega.getCodigo(), excecao.getMessage());
        }
    }
}
