package br.com.ricardofigueiredo.gateway.webhook;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Alvo de treino para quem esta escrevendo o outro lado do webhook.
 *
 * Aceita qualquer POST, responde 200 e devolve o cabecalho de assinatura que
 * recebeu junto com o resultado da conferencia. Assim da para ver, sem montar
 * servidor nenhum, se a assinatura que sai daqui e mesmo verificavel.
 *
 * Nada e gravado. O segredo precisa ser enviado no corpo da chamada para que a
 * conferencia possa acontecer, e por isso mesmo isto serve para experimento e
 * nao para receber evento de verdade.
 */
@RestController
@Tag(name = "Webhooks", description = "Endpoints que recebem os eventos das cobrancas")
public class EcoController {

    @PostMapping("/api/v1/webhooks/eco")
    @Operation(summary = "Devolve 200 e mostra a assinatura recebida",
            description = """
                    Aponte um endpoint para ca para ver a entrega funcionando de ponta a ponta.
                    Informe o segredo no parametro para que a resposta diga se a assinatura confere.""")
    public Map<String, Object> ecoar(
            @RequestHeader(name = AssinaturaDeWebhook.CABECALHO, required = false) String assinatura,
            @RequestHeader(name = "Aval-Evento", required = false) String evento,
            @RequestHeader(name = "Aval-Entrega", required = false) String entrega,
            @org.springframework.web.bind.annotation.RequestParam(required = false) String segredo,
            @RequestBody(required = false) String corpo) {

        Map<String, Object> resposta = new LinkedHashMap<>();
        resposta.put("recebidoEm", Instant.now().toString());
        resposta.put("evento", evento);
        resposta.put("entrega", entrega);
        resposta.put("assinatura", assinatura);
        resposta.put("tamanhoDoCorpo", corpo == null ? 0 : corpo.length());

        if (segredo != null && corpo != null) {
            resposta.put("assinaturaConfere",
                    AssinaturaDeWebhook.confere(segredo, corpo, assinatura, Instant.now()));
        }

        return resposta;
    }
}
