package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.cobranca.Cobranca;
import br.com.ricardofigueiredo.gateway.cobranca.dto.CobrancaResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Caixa de saida.
 *
 * A entrega e gravada dentro da mesma transacao que mudou a cobranca, e nao
 * disparada na hora pela rede. Sao duas garantias de uma vez: se a transacao
 * voltar atras, nenhum aviso falso sai; e se o processo cair logo depois do
 * commit, o aviso continua gravado esperando a proxima rodada do entregador.
 * Disparar HTTP dentro da transacao daria o problema oposto nas duas pontas.
 */
@Component
public class EmissorDeEventos {

    private static final Logger log = LoggerFactory.getLogger(EmissorDeEventos.class);

    private final EndpointWebhookRepository endpointRepository;
    private final EntregaWebhookRepository entregaRepository;
    private final ObjectMapper objectMapper;

    public EmissorDeEventos(EndpointWebhookRepository endpointRepository,
                            EntregaWebhookRepository entregaRepository,
                            ObjectMapper objectMapper) {
        this.endpointRepository = endpointRepository;
        this.entregaRepository = entregaRepository;
        this.objectMapper = objectMapper;
    }

    public void emitir(String evento, Cobranca cobranca) {
        List<EndpointWebhook> destinos =
                endpointRepository.findByUsuarioAndAtivoTrue(cobranca.getUsuario());

        if (destinos.isEmpty()) {
            return;
        }

        String corpo = montarCorpo(evento, cobranca);
        destinos.forEach(destino -> entregaRepository.save(new EntregaWebhook(destino, evento, corpo)));

        log.info("evento {} enfileirado para {} endpoint(s)", evento, destinos.size());
    }

    private String montarCorpo(String evento, Cobranca cobranca) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("evento", evento);
        envelope.put("criadoEm", Instant.now().toString());
        envelope.put("dados", CobrancaResponse.de(cobranca));

        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (JsonProcessingException excecao) {
            throw new IllegalStateException("nao foi possivel serializar o evento " + evento, excecao);
        }
    }
}
