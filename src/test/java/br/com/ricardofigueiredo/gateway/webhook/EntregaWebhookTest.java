package br.com.ricardofigueiredo.gateway.webhook;

import br.com.ricardofigueiredo.gateway.usuario.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntregaWebhookTest {

    @Test
    @DisplayName("entrega aceita sai da fila e guarda o codigo de resposta")
    void sucessoEncerraAEntrega() {
        EntregaWebhook entrega = novaEntrega();
        entrega.registrarSucesso(200);

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.ENTREGUE);
        assertThat(entrega.getUltimoCodigoHttp()).isEqualTo(200);
        assertThat(entrega.getProximaTentativaEm()).isNull();
        assertThat(entrega.getConcluidoEm()).isNotNull();
        assertThat(entrega.getTentativas()).isEqualTo(1);
    }

    @Test
    @DisplayName("a espera entre tentativas cresce a cada falha")
    void esperaCresceACadaFalha() {
        EntregaWebhook entrega = novaEntrega();

        for (int tentativa = 0; tentativa < EntregaWebhook.ESPERAS.size(); tentativa++) {
            Instant antes = Instant.now();
            entrega.registrarFalha(503, "o endpoint respondeu 503");

            assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
            Duration esperada = EntregaWebhook.ESPERAS.get(tentativa);
            assertThat(entrega.getProximaTentativaEm())
                    .isBetween(antes.plus(esperada).minusSeconds(5), antes.plus(esperada).plusSeconds(5));
        }
    }

    @Test
    @DisplayName("depois da ultima espera a entrega para de tentar")
    void desisteDepoisDaUltimaTentativa() {
        EntregaWebhook entrega = novaEntrega();

        for (int tentativa = 0; tentativa <= EntregaWebhook.ESPERAS.size(); tentativa++) {
            entrega.registrarFalha(500, "erro no endpoint");
        }

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.FALHOU);
        assertThat(entrega.getProximaTentativaEm()).isNull();
        assertThat(entrega.getTentativas()).isEqualTo(EntregaWebhook.ESPERAS.size() + 1);
    }

    @Test
    @DisplayName("o motivo da falha e cortado para caber na coluna")
    void motivoLongoECortado() {
        EntregaWebhook entrega = novaEntrega();
        entrega.registrarFalha(500, "x".repeat(600));

        assertThat(entrega.getUltimaFalha()).hasSize(300);
    }

    @Test
    @DisplayName("falha sem codigo de resposta e aceita, porque conexao recusada nao tem codigo")
    void falhaSemCodigoHttp() {
        EntregaWebhook entrega = novaEntrega();
        entrega.registrarFalha(null, null);

        assertThat(entrega.getUltimoCodigoHttp()).isNull();
        assertThat(entrega.getUltimaFalha()).isNull();
        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
    }

    @Test
    @DisplayName("reenvio devolve a entrega para a fila sem zerar o contador")
    void reenvioVoltaParaAFila() {
        EntregaWebhook entrega = novaEntrega();
        entrega.registrarSucesso(200);
        entrega.reenfileirar();

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
        assertThat(entrega.getConcluidoEm()).isNull();
        assertThat(entrega.getProximaTentativaEm()).isNotNull();
        assertThat(entrega.getTentativas()).isEqualTo(1);
    }

    @Test
    @DisplayName("a entrega nasce pendente, pronta para a proxima rodada")
    void nasceNaFila() {
        EntregaWebhook entrega = novaEntrega();

        assertThat(entrega.getSituacao()).isEqualTo(SituacaoDaEntrega.PENDENTE);
        assertThat(entrega.getCodigo()).startsWith("evt_");
        assertThat(entrega.getEvento()).isEqualTo("cobranca.capturada");
        assertThat(entrega.getTentativas()).isZero();
    }

    private EntregaWebhook novaEntrega() {
        Usuario usuario = new Usuario("loja@exemplo.com", "hash", "Loja", "loja@exemplo.com", "NITEROI");
        EndpointWebhook endpoint = new EndpointWebhook(usuario, "https://exemplo.com/eventos", "producao");
        return new EntregaWebhook(endpoint, "cobranca.capturada", "{}");
    }
}
